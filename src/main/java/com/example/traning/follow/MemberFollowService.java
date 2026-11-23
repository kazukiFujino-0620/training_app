package com.example.traning.follow;

import com.example.traning.dao.UserDao;
import com.example.traning.follow.MemberFollowEvaluator.Facts;
import com.example.traning.follow.MemberFollowEvaluator.Result;
import com.example.traning.goal.FrequencyGoalService;
import com.example.traning.goal.FrequencyGoalService.MemberGoalStatus;
import com.example.traning.goal.FrequencyGoalSummary;
import com.example.traning.goal.GoalProposalService;
import com.example.traning.goal.GoalProposalService.ReviewStatus;
import com.example.traning.health.HealthInfoAccessPolicy;
import com.example.traning.organization.MemberAccessLevel;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.OrganizationType;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.smarttrainer.autogen.UserExcludedItem;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.UserTrainingDate;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 担当会員の一覧と「フォロー推奨」の印（機能見直し-1-#12）。
 *
 * <ul>
 *   <li>「自分の担当」: 担当トレーナーが自分の会員。トレーナー・店舗管理者・ジム管理者
 *   <li>「店舗全体」: 店舗管理者は自分の店舗と兼任の店舗、ジム管理者は自分のジムの店舗と「ジム全体」、システム管理者は全ジムの店舗と各「ジム全体」（Q12-1
 *       B）。範囲はサーバーで検証する（範囲外は403）
 * </ul>
 *
 * <p>印と理由はスタッフにだけ見せ、会員には出さない。痛みの報告の理由の文言は {@link HealthInfoAccessPolicy}に従う。
 */
@Service
public class MemberFollowService {

  /** 一覧のタブ。 */
  public enum Tab {
    MINE,
    STORE
  }

  /** 並べ替え。 */
  public enum Sort {
    /** フォロー推奨が先（初期値）。 */
    FLAG,
    /** 最終日が古い順。 */
    LAST,
    /** 名前順。 */
    NAME,
    /** 担当者順（店舗全体のみ）。 */
    TRAINER;

    public String label() {
      return switch (this) {
        case FLAG -> "フォロー推奨が先";
        case LAST -> "最終日が古い順";
        case NAME -> "名前順";
        case TRAINER -> "担当者順";
      };
    }
  }

  /** 店舗全体の切り替えの選択肢。{@code value}は {@code store:ID} または {@code gym:ID}。 */
  public record ScopeOption(String value, String label) {}

  /**
   * 一覧の1行。
   *
   * @param trainerName 担当トレーナーの名前。未割当はnull
   * @param goalLabel 例: 「週3回」「週4回（プログラム）」「目標なし」
   * @param resultLabel 例: 「直近4週 1/4（25%）」。目標なしはnull
   * @param resultShortLabel 店舗全体の列用。例: 「1/4（25%）」。目標なしはnull
   * @param thisWeekLabel 例: 「0/3回」。目標なしは「—」
   * @param daysSinceLastTraining 最後のトレーニングからの日数。記録が無ければnull
   * @param canAcknowledge 「対応済みにする」を押せるか
   * @param nextReviewDate 目標の次の見直し日（機能見直し-1-#20。提案を受けたことが無ければnull＝未設定）
   * @param reviewDateLabel 見直し日の表示。例: 「12/22（今日）」「12/20（2日前）」「1/19」。未設定はnull
   * @param reviewDue 見直し日の青い印を出すか（見直し日の当日以降で、まだ新しい提案を送っていない）
   * @param canPropose 「目標を提案する」を押せるか
   */
  public record MemberRow(
      Long memberId,
      String name,
      String trainerName,
      String goalLabel,
      String resultLabel,
      String resultShortLabel,
      boolean deloadWeek,
      String thisWeekLabel,
      LocalDate lastTrainingDate,
      Long daysSinceLastTraining,
      Result follow,
      boolean canAcknowledge,
      LocalDate nextReviewDate,
      String reviewDateLabel,
      boolean reviewDue,
      boolean canPropose) {

    /** 期分けプログラムの週・デロード週は、目標と達成率を2行に分けて出す（仮画面どおり）。 */
    public boolean goalOnTwoLines() {
      return deloadWeek || (goalLabel != null && goalLabel.endsWith("（プログラム）"));
    }
  }

  /** 一覧画面の表示データ。 */
  public record ListView(
      Tab tab,
      List<Tab> tabs,
      Sort sort,
      List<ScopeOption> scopeOptions,
      String selectedScope,
      List<MemberRow> rows) {

    /** タブ・並べ替えを切り替えるリンク。店舗全体の選択は引き継ぐ。 */
    public String url(Tab toTab, Sort toSort) {
      UriComponentsBuilder b = UriComponentsBuilder.fromPath("/trainer/members");
      b.queryParam("tab", toTab.name().toLowerCase(Locale.ROOT));
      if (toTab == Tab.STORE && selectedScope != null) b.queryParam("scope", selectedScope);
      b.queryParam("sort", toSort.name().toLowerCase(Locale.ROOT));
      return b.encode().build().toUriString();
    }

    public boolean storeTab() {
      return tab == Tab.STORE;
    }

    /** 並べ替えの選択肢（担当者順は店舗全体のみ）。 */
    public List<Sort> sorts() {
      return storeTab()
          ? List.of(Sort.FLAG, Sort.LAST, Sort.NAME, Sort.TRAINER)
          : List.of(Sort.FLAG, Sort.LAST, Sort.NAME);
    }
  }

  private final MemberFollowDao memberFollowDao;
  private final UserDao userDao;
  private final TrainingDao trainingDao;
  private final OrganizationDao organizationDao;
  private final OrganizationScopeResolver organizationScopeResolver;
  private final MemberAccessPolicy memberAccessPolicy;
  private final HealthInfoAccessPolicy healthInfoAccessPolicy;
  private final FrequencyGoalService frequencyGoalService;
  private final PeriodizationService periodizationService;
  private final GoalProposalService goalProposalService;

  public MemberFollowService(
      MemberFollowDao memberFollowDao,
      UserDao userDao,
      TrainingDao trainingDao,
      OrganizationDao organizationDao,
      OrganizationScopeResolver organizationScopeResolver,
      MemberAccessPolicy memberAccessPolicy,
      HealthInfoAccessPolicy healthInfoAccessPolicy,
      FrequencyGoalService frequencyGoalService,
      PeriodizationService periodizationService,
      GoalProposalService goalProposalService) {
    this.memberFollowDao = memberFollowDao;
    this.userDao = userDao;
    this.trainingDao = trainingDao;
    this.organizationDao = organizationDao;
    this.organizationScopeResolver = organizationScopeResolver;
    this.memberAccessPolicy = memberAccessPolicy;
    this.healthInfoAccessPolicy = healthInfoAccessPolicy;
    this.frequencyGoalService = frequencyGoalService;
    this.periodizationService = periodizationService;
    this.goalProposalService = goalProposalService;
  }

  // ===================== 参照系 =====================

  /** 役割ごとに使えるタブ。システム管理者は担当トレーナーになれないため「店舗全体」のみ。 */
  public static List<Tab> availableTabs(User viewer) {
    Role role = Role.fromValue(viewer.getRole());
    if (role == null) return List.of();
    return switch (role) {
      case TRAINER -> List.of(Tab.MINE);
      case STORE_ADMIN, ORG_ADMIN -> List.of(Tab.MINE, Tab.STORE);
      case ADMIN -> List.of(Tab.STORE);
      default -> List.of();
    };
  }

  /**
   * 一覧の表示データ。
   *
   * @param tab null なら役割の初期タブ
   * @param scope 店舗全体の選択（{@code store:ID}／{@code gym:ID}）。null なら初期の選択肢
   * @param sort null ならフォロー推奨が先
   * @throws ResponseStatusException 使えないタブ・範囲外の店舗やジムを指定した場合は403
   */
  @Transactional
  public ListView list(User viewer, Tab tab, String scope, Sort sort, LocalDate today) {
    List<Tab> tabs = availableTabs(viewer);
    if (tabs.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "担当会員の一覧を見る権限がありません");
    }
    Tab effectiveTab = tab == null ? tabs.get(0) : tab;
    if (!tabs.contains(effectiveTab)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この一覧を見る権限がありません");
    }
    Sort effectiveSort = sort == null ? Sort.FLAG : sort;
    if (effectiveSort == Sort.TRAINER && effectiveTab != Tab.STORE) {
      effectiveSort = Sort.FLAG;
    }

    List<ScopeOption> options = List.of();
    String selectedScope = null;
    List<User> members;
    if (effectiveTab == Tab.MINE) {
      members = assignedMembers(viewer);
    } else {
      options = scopeOptions(viewer);
      selectedScope = scope == null || scope.isBlank() ? defaultScope(viewer, options) : scope;
      List<Long> organizationIds = resolveScope(viewer, selectedScope);
      members =
          organizationIds.isEmpty()
              ? List.of()
              : memberFollowDao.selectMembersByOrganizationIds(organizationIds).stream()
                  .filter(m -> memberAccessPolicy.resolve(viewer, m) == MemberAccessLevel.FULL)
                  .toList();
    }

    List<MemberRow> rows = buildRows(viewer, members, today, LocalDateTime.now(), true);
    return new ListView(
        effectiveTab, tabs, effectiveSort, options, selectedScope, sortRows(rows, effectiveSort));
  }

  /** トップのボタンの「フォロー推奨 N人」。自分の担当で印が付いている人数。 */
  @Transactional
  public int countFlaggedAssigned(User viewer, LocalDate today) {
    if (!availableTabs(viewer).contains(Tab.MINE)) return 0;
    List<User> members = assignedMembers(viewer);
    if (members.isEmpty()) return 0;
    return (int)
        buildRows(viewer, members, today, LocalDateTime.now(), false).stream()
            .filter(r -> r.follow().flagged())
            .count();
  }

  /** トップのボタンの「見直し N人」（機能見直し-1-#20）。自分の担当で見直し日の印が付いている人数。 */
  @Transactional(readOnly = true)
  public int countReviewDueAssigned(User viewer, LocalDate today) {
    if (!availableTabs(viewer).contains(Tab.MINE)) return 0;
    List<User> members = assignedMembers(viewer);
    if (members.isEmpty()) return 0;
    return (int)
        goalProposalService
            .reviewStatuses(members.stream().map(m -> m.getUserId().longValue()).toList(), today)
            .values()
            .stream()
            .filter(ReviewStatus::due)
            .count();
  }

  private List<User> assignedMembers(User viewer) {
    return memberFollowDao.selectMembersByAssignedTrainerId(viewer.getUserId().longValue()).stream()
        .filter(m -> memberAccessPolicy.resolve(viewer, m) == MemberAccessLevel.FULL)
        .toList();
  }

  // ===================== 店舗全体の範囲 =====================

  /** 店舗全体の切り替えの選択肢。 */
  List<ScopeOption> scopeOptions(User viewer) {
    Role role = Role.fromValue(viewer.getRole());
    List<ScopeOption> options = new ArrayList<>();
    if (role == Role.ADMIN) {
      List<Organization> all = organizationDao.selectAll();
      for (Organization gym : all) {
        if (!OrganizationType.GYM.name().equals(gym.getType())) continue;
        options.add(new ScopeOption("gym:" + gym.getId(), gym.getName() + "（ジム全体）"));
        for (Organization store : organizationDao.selectByParentOrganizationId(gym.getId())) {
          options.add(new ScopeOption("store:" + store.getId(), store.getName()));
        }
      }
    } else if (role == Role.ORG_ADMIN) {
      Optional<Organization> gym =
          organizationDao
              .selectById(viewer.getOrganizationId())
              .filter(o -> OrganizationType.GYM.name().equals(o.getType()));
      if (gym.isPresent()) {
        options.add(new ScopeOption("gym:" + gym.get().getId(), "ジム全体"));
        for (Organization store : organizationDao.selectByParentOrganizationId(gym.get().getId())) {
          options.add(new ScopeOption("store:" + store.getId(), store.getName()));
        }
      }
    } else if (role == Role.STORE_ADMIN) {
      Set<Long> ids = organizationScopeResolver.resolveAccessibleOrganizationIds(viewer);
      if (ids != null && !ids.isEmpty()) {
        organizationDao.selectByIds(List.copyOf(ids)).stream()
            .filter(o -> OrganizationType.STORE.name().equals(o.getType()))
            .sorted(
                Comparator.comparing(
                        (Organization o) -> !o.getId().equals(viewer.getOrganizationId()))
                    .thenComparing(Organization::getId))
            .forEach(o -> options.add(new ScopeOption("store:" + o.getId(), o.getName())));
      }
    }
    return options;
  }

  /** 初期の選択肢。店舗管理者は自分の店舗、ジム管理者は「ジム全体」、システム管理者は先頭。 */
  private static String defaultScope(User viewer, List<ScopeOption> options) {
    String own = "store:" + viewer.getOrganizationId();
    if (options.stream().anyMatch(o -> o.value().equals(own))) return own;
    return options.isEmpty() ? null : options.get(0).value();
  }

  /**
   * 選択から対象の組織IDを求める。範囲外・形式不正は403。
   *
   * <p>ジム全体はジム自身と配下の店舗。店舗管理者はジム全体を選べない。
   */
  List<Long> resolveScope(User viewer, String scope) {
    if (scope == null) return List.of();
    Role role = Role.fromValue(viewer.getRole());
    String[] parts = scope.split(":", 2);
    Long id = parseId(parts.length == 2 ? parts[1] : null);
    if (id == null) throw forbiddenScope();
    Organization org =
        organizationDao.selectById(id).orElseThrow(MemberFollowService::forbiddenScope);
    if ("store".equals(parts[0]) && OrganizationType.STORE.name().equals(org.getType())) {
      if (!organizationScopeResolver.canAccessOrganization(viewer, id)) throw forbiddenScope();
      return List.of(id);
    }
    if ("gym".equals(parts[0])
        && OrganizationType.GYM.name().equals(org.getType())
        && (role == Role.ADMIN
            || (role == Role.ORG_ADMIN && id.equals(viewer.getOrganizationId())))) {
      List<Long> ids = new ArrayList<>();
      ids.add(id);
      organizationDao.selectByParentOrganizationId(id).forEach(s -> ids.add(s.getId()));
      return ids;
    }
    throw forbiddenScope();
  }

  private static Long parseId(String s) {
    try {
      return s == null ? null : Long.valueOf(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static ResponseStatusException forbiddenScope() {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, "この店舗の一覧を見る権限がありません");
  }

  // ===================== 対応済み =====================

  /**
   * 「対応済みにする」。担当トレーナー、または範囲内の管理者（店舗・ジム・システム管理者）が押せる。トレーナーは担当会員のみ。 押した時点で付いている印の理由の種類・押した人・日時を残す。
   *
   * @throws ResponseStatusException 押せない会員は403
   * @throws IllegalArgumentException 印が付いていない会員の場合
   */
  @Transactional
  public void acknowledge(User viewer, Long memberId, LocalDate today, LocalDateTime now) {
    User member = userDao.selectById(memberId.intValue());
    if (member == null || member.getDeletedAt() != null || !canAcknowledge(viewer, member)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この会員を対応済みにする権限がありません");
    }
    MemberRow row = buildRows(viewer, List.of(member), today, now, false).get(0);
    if (!row.follow().flagged()) {
      throw new IllegalArgumentException("この会員にはフォロー推奨の印が付いていません");
    }
    MemberFollowAcknowledgement ack = new MemberFollowAcknowledgement();
    ack.setMemberUserId(memberId);
    ack.setReasonTypes(MemberFollowEvaluator.formatTypes(row.follow().activeTypes()));
    ack.setActedBy(viewer.getUserId().longValue());
    ack.setActedAt(now);
    memberFollowDao.insertAcknowledgement(ack);
  }

  /** トレーナーは担当会員のみ。管理者は範囲内の会員（担当でなくてもよい）。 */
  boolean canAcknowledge(User viewer, User member) {
    return memberAccessPolicy.isResponsibleStaff(viewer, member);
  }

  // ===================== 行の組み立て =====================

  /**
   * 会員ごとの材料をまとめて集めて判定する（会員ごとのSQLは、#7の未確定の週の確定と痛みの報告がある会員の閲覧可否だけ）。
   *
   * @param recordHealthView 痛みの報告の理由の文言を表示するか（一覧の表示のみtrue。管理者がジムの設定にもとづいて見る場合は監査ログに記録する）
   */
  List<MemberRow> buildRows(
      User viewer,
      List<User> members,
      LocalDate today,
      LocalDateTime now,
      boolean recordHealthView) {
    if (members.isEmpty()) return List.of();
    List<Long> ids = members.stream().map(m -> m.getUserId().longValue()).toList();

    Map<Long, LocalDate> lastTraining = new HashMap<>();
    for (UserTrainingDate d : trainingDao.selectLastTrainingDatesByUserIds(ids, today)) {
      lastTraining.put(d.getUserId(), d.getTrainingDate());
    }
    Map<Long, MemberGoalStatus> goals = frequencyGoalService.getStatusesForMembers(ids, today);
    Set<Long> deload = periodizationService.findMembersInDeloadWeek(ids, today);
    Map<Long, List<UserExcludedItem>> pain = new HashMap<>();
    for (UserExcludedItem e :
        memberFollowDao.selectReasonedExclusionsSince(
            ids, now.minusDays(MemberFollowEvaluator.PAIN_REPORT_DAYS))) {
      pain.computeIfAbsent(e.getUserId(), k -> new ArrayList<>()).add(e);
    }
    Map<Long, MemberFollowAcknowledgement> acks = new HashMap<>();
    for (MemberFollowAcknowledgement a :
        memberFollowDao.selectLatestAcknowledgementsByMemberIds(ids)) {
      acks.put(a.getMemberUserId(), a);
    }
    Map<Long, String> trainerNames = trainerNames(members);
    Map<Long, ReviewStatus> reviews = goalProposalService.reviewStatuses(ids, today);

    List<MemberRow> rows = new ArrayList<>();
    for (User m : members) {
      long id = m.getUserId().longValue();
      List<UserExcludedItem> painReports = pain.getOrDefault(id, List.of());
      boolean painVisible = false;
      if (recordHealthView && !painReports.isEmpty() && !deload.contains(id)) {
        if (healthInfoAccessPolicy.canView(viewer, m)) {
          // 管理者がジムの設定にもとづいて見る場合は監査ログに記録する
          healthInfoAccessPolicy.requireViewAndRecord(viewer, m, "user_excluded_items");
          painVisible = true;
        }
      }
      LocalDateTime assignedAt = m.getAssignedTrainerId() == null ? null : m.getAssignedTrainerAt();
      LocalDate base =
          assignedAt != null
              ? assignedAt.toLocalDate()
              : (m.getCreateDatetime() == null ? null : m.getCreateDatetime().toLocalDate());
      MemberGoalStatus goal = goals.get(id);
      Facts facts =
          new Facts(
              lastTraining.get(id),
              base,
              assignedAt != null,
              goal == null ? List.of() : goal.lastCompletedWeeks(),
              deload.contains(id),
              painReports,
              painVisible,
              acks.get(id));
      Result result = MemberFollowEvaluator.evaluate(facts, today);
      FrequencyGoalSummary summary = goal == null ? null : goal.summary();
      LocalDate last = lastTraining.get(id);
      ReviewStatus review = reviews.get(id);
      rows.add(
          new MemberRow(
              id,
              m.getUserName(),
              m.getAssignedTrainerId() == null ? null : trainerNames.get(m.getAssignedTrainerId()),
              goalLabel(summary),
              resultLabel(summary),
              resultShortLabel(summary),
              deload.contains(id),
              thisWeekLabel(summary),
              last,
              last == null ? null : ChronoUnit.DAYS.between(last, today),
              result,
              result.flagged() && canAcknowledge(viewer, m),
              review == null ? null : review.nextReviewDate(),
              review == null ? null : reviewDateLabel(review.nextReviewDate(), today),
              review != null && review.due(),
              goalProposalService.canPropose(viewer, m)));
    }
    return rows;
  }

  private Map<Long, String> trainerNames(List<User> members) {
    List<Long> trainerIds =
        members.stream()
            .map(User::getAssignedTrainerId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Map<Long, String> names = new HashMap<>();
    if (trainerIds.isEmpty()) return names;
    for (User t : memberFollowDao.selectUsersByIds(trainerIds)) {
      names.put(t.getUserId().longValue(), t.getUserName());
    }
    return names;
  }

  static String goalLabel(FrequencyGoalSummary s) {
    if (s == null || !s.goalSet()) return "目標なし";
    String program = s.thisWeekFromProgram() ? "（プログラム）" : "";
    if (s.thisWeekTarget() == null || s.thisWeekTarget() == 0) return "今週の予定なし" + program;
    return "週" + s.thisWeekTarget() + "回" + program;
  }

  /** 例: 「直近4週 1/4（25%）」「直近2週 1/2」「達成率は来週から」。 */
  static String resultLabel(FrequencyGoalSummary s) {
    if (s == null || !s.goalSet()) return null;
    if (s.evaluatedWeeks() == 0) return "達成率は来週から";
    Integer pct = s.achievementPct();
    return "直近"
        + s.evaluatedWeeks()
        + "週 "
        + s.achievedWeeks()
        + "/"
        + s.evaluatedWeeks()
        + (pct == null ? "" : "（" + pct + "%）");
  }

  /** 例: 「1/4（25%）」「1/2」「達成率は来週から」。 */
  static String resultShortLabel(FrequencyGoalSummary s) {
    if (s == null || !s.goalSet()) return null;
    if (s.evaluatedWeeks() == 0) return "達成率は来週から";
    Integer pct = s.achievementPct();
    return s.achievedWeeks() + "/" + s.evaluatedWeeks() + (pct == null ? "" : "（" + pct + "%）");
  }

  static String thisWeekLabel(FrequencyGoalSummary s) {
    if (s == null || !s.goalSet()) return "—";
    if (s.thisWeekTarget() == null || s.thisWeekTarget() == 0) return s.thisWeekDoneDays() + "回";
    return s.thisWeekDoneDays() + "/" + s.thisWeekTarget() + "回";
  }

  /** 例: 「12/22（今日）」「12/20（2日前）」「1/19」。 */
  static String reviewDateLabel(LocalDate date, LocalDate today) {
    String md = date.getMonthValue() + "/" + date.getDayOfMonth();
    long days = ChronoUnit.DAYS.between(date, today);
    if (days == 0) return md + "（今日）";
    if (days > 0) return md + "（" + days + "日前）";
    return md;
  }

  // ===================== 並べ替え =====================

  static List<MemberRow> sortRows(List<MemberRow> rows, Sort sort) {
    Collator collator = Collator.getInstance(Locale.JAPANESE);
    Comparator<MemberRow> byName =
        Comparator.comparing(r -> r.name() == null ? "" : r.name(), collator);
    // 記録が無い会員を最も古い扱いにする
    Comparator<MemberRow> byLast =
        Comparator.comparing(
            MemberRow::lastTrainingDate, Comparator.nullsFirst(Comparator.naturalOrder()));
    // 機能見直し-1-#20: フォロー推奨の次に見直し日の印（Q20-2 A）
    Comparator<MemberRow> flaggedFirst =
        Comparator.comparing((MemberRow r) -> !r.follow().flagged())
            .thenComparing(r -> !r.reviewDue());
    Comparator<MemberRow> comparator =
        switch (sort) {
          case FLAG -> flaggedFirst.thenComparing(byLast).thenComparing(byName);
          case LAST -> byLast.thenComparing(byName);
          case NAME -> byName;
          case TRAINER ->
              Comparator.comparing(MemberRow::trainerName, Comparator.nullsLast(collator::compare))
                  .thenComparing(flaggedFirst)
                  .thenComparing(byLast)
                  .thenComparing(byName);
        };
    return rows.stream().sorted(comparator).toList();
  }
}
