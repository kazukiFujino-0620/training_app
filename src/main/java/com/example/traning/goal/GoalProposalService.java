package com.example.traning.goal;

import com.example.traning.dao.UserDao;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 目標をトレーナーと一緒に決める（機能見直し-1-#20）。
 *
 * <ul>
 *   <li>提案できるのは担当トレーナーと、担当範囲の店舗・ジム・システム管理者（#12の「対応済みにする」と同じ。{@link
 *       MemberAccessPolicy#isResponsibleStaff}）。担当外のトレーナーは不可
 *   <li>提案の中身は週の回数目標・目的モード・ひとこと（任意、50文字まで）・次の見直し日。体重・体型の目標は持たない
 *   <li>返事待ちのうちに新しい提案を送ると、前の提案は取り下げ
 *   <li>会員が承認すると、週の回数目標（#7の「目標を設定した週」と同じ扱い。プログラム実施中の週はプログラム優先）と目的モードを確定する
 *   <li>会員は今までどおり自分で変えられる（Q20-1 A）。変えると「担当トレーナーと決めた目標」の表示が外れ、履歴に「会員が変更」と残る
 * </ul>
 */
@Service
public class GoalProposalService {

  /** ひとことの上限（文字数）。 */
  public static final int COMMENT_MAX_LENGTH = 50;

  /** 次の見直し日の初期値（提案日から）。期分けサイクルの終わり4週を目安にした値（会議の決定、根拠なし）。 */
  public static final int DEFAULT_REVIEW_DAYS = 28;

  /** 次の見直し日に選べる最も先の日（提案日から）。 */
  public static final int MAX_REVIEW_DAYS = 365;

  private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");

  private final GoalProposalDao goalProposalDao;
  private final UserDao userDao;
  private final MemberAccessPolicy memberAccessPolicy;
  private final FrequencyGoalService frequencyGoalService;
  private final ProfileService profileService;

  public GoalProposalService(
      GoalProposalDao goalProposalDao,
      UserDao userDao,
      MemberAccessPolicy memberAccessPolicy,
      FrequencyGoalService frequencyGoalService,
      ProfileService profileService) {
    this.goalProposalDao = goalProposalDao;
    this.userDao = userDao;
    this.memberAccessPolicy = memberAccessPolicy;
    this.frequencyGoalService = frequencyGoalService;
    this.profileService = profileService;
  }

  // ===================== 表示データ =====================

  /** 履歴の1行（新しい順）。 */
  public record HistoryRow(String dateLabel, String content, String statusLabel) {}

  /**
   * スタッフ向けの会員詳細の「目標」カード。
   *
   * @param currentLabel 例: 「週2回・維持（会員が設定）」
   * @param pendingLabel 返事待ちの提案があれば「返事待ちの提案があります（11/24 山本）」。無ければnull
   * @param programActive 会員が期分けプログラムを実施中か（プログラム優先の注意を出す）
   */
  public record StaffGoalView(
      String currentLabel,
      int defaultWeeklyTarget,
      String defaultGoalMode,
      LocalDate defaultReviewDate,
      LocalDate minReviewDate,
      LocalDate maxReviewDate,
      boolean programActive,
      String pendingLabel,
      List<HistoryRow> history) {}

  /**
   * 会員のトップに出す返事待ちの提案。
   *
   * @param currentWeeklyLabel 今の週の回数目標（例: 「週2回」、未設定は「未設定」）
   */
  public record MemberProposalView(
      Long id,
      String proposerName,
      String currentWeeklyLabel,
      String proposedWeeklyLabel,
      String currentGoalModeLabel,
      String proposedGoalModeLabel,
      String comment,
      LocalDate nextReviewDate,
      String nextReviewLabel) {

    public boolean weeklyChanged() {
      return !Objects.equals(currentWeeklyLabel, proposedWeeklyLabel);
    }

    public boolean goalModeChanged() {
      return !Objects.equals(currentGoalModeLabel, proposedGoalModeLabel);
    }

    /** 例: 「山本トレーナーから目標の提案があります」。 */
    public String title() {
      return (proposerName != null ? proposerName + "トレーナー" : "トレーナー") + "から目標の提案があります";
    }
  }

  /**
   * プロフィールの「担当トレーナーと決めた目標」の表示（今の目標が承認した提案と同じ間だけ）。
   *
   * @param tagLabel 例: 「担当トレーナーと決めた目標（11/24 山本）」
   */
  public record AgreementView(String tagLabel, LocalDate nextReviewDate, String nextReviewLabel) {}

  /**
   * 担当会員の一覧（#12）の「目標の見直し」列。
   *
   * @param nextReviewDate 最新の提案の次の見直し日（提案が無ければnull＝未設定）
   * @param due 見直し日の当日以降で、まだ新しい提案を送っていない（青い印「見直し日」）
   */
  public record ReviewStatus(LocalDate nextReviewDate, boolean due) {}

  /** 会員詳細に目標のカードを出してよいか（提案できる人だけ）。 */
  public boolean canPropose(User viewer, User member) {
    return member != null
        && member.getDeletedAt() == null
        && memberAccessPolicy.isResponsibleStaff(viewer, member);
  }

  /**
   * スタッフ向けの会員詳細の「目標」カード。
   *
   * @throws ResponseStatusException 提案できない会員は403
   */
  @Transactional
  public StaffGoalView staffView(User viewer, User member, LocalDate today) {
    requireCanPropose(viewer, member);
    long memberId = member.getUserId().longValue();
    List<GoalProposal> proposals = goalProposalDao.selectProposalsByMember(memberId);
    List<MemberGoalChange> changes = goalProposalDao.selectChangesByMember(memberId);
    Optional<Integer> manual = frequencyGoalService.findManualTarget(memberId);
    String mode = GoalMode.fromString(member.getCurrentGoalMode()).name();
    boolean agreed = agreedProposal(proposals, changes, manual, mode).isPresent();

    String current =
        manual.map(n -> "週" + n + "回").orElse("週の回数目標なし")
            + "・"
            + goalModeLabel(mode)
            + (agreed ? "（担当トレーナーと決めた目標）" : "（会員が設定）");
    String pendingLabel =
        proposals.stream()
            .filter(p -> GoalProposalStatus.PENDING.name().equals(p.getStatus()))
            .findFirst()
            .map(
                p ->
                    "返事待ちの提案があります（"
                        + p.getProposedAt().toLocalDate().format(MD)
                        + nameSuffix(p.getProposerName())
                        + "）")
            .orElse(null);
    boolean programActive = frequencyGoalService.getSummary(memberId, today).programActive();
    return new StaffGoalView(
        current,
        manual.orElse(3),
        mode,
        today.plusDays(DEFAULT_REVIEW_DAYS),
        today.plusDays(1),
        today.plusDays(MAX_REVIEW_DAYS),
        programActive,
        pendingLabel,
        history(proposals, changes));
  }

  /** 履歴（提案と会員が自分で変えた記録を新しい順に並べる）。 */
  static List<HistoryRow> history(List<GoalProposal> proposals, List<MemberGoalChange> changes) {
    record Entry(LocalDateTime at, long tiebreak, HistoryRow row) {}
    List<Entry> entries = new ArrayList<>();
    for (GoalProposal p : proposals) {
      StringBuilder content = new StringBuilder();
      content.append("週").append(p.getWeeklyTargetCount()).append("回・");
      content.append(goalModeLabel(p.getGoalMode()));
      if (p.getComment() != null && !p.getComment().isBlank()) {
        content.append("・「").append(p.getComment()).append("」");
      }
      content.append("・見直し日").append(p.getNextReviewDate().format(MD));
      content
          .append("（")
          .append(p.getProposerName() != null ? p.getProposerName() : "スタッフ")
          .append("が提案）");
      GoalProposalStatus st = GoalProposalStatus.valueOf(p.getStatus());
      String status =
          st == GoalProposalStatus.PENDING || p.getRespondedAt() == null
              ? st.label()
              : st.label() + "（" + p.getRespondedAt().toLocalDate().format(MD) + "）";
      entries.add(
          new Entry(
              p.getProposedAt(),
              p.getId() == null ? 0 : p.getId(),
              new HistoryRow(
                  p.getProposedAt().toLocalDate().format(MD), content.toString(), status)));
    }
    for (MemberGoalChange c : changes) {
      String content =
          MemberGoalChange.WEEKLY_TARGET.equals(c.getChangeType())
              ? "週の回数目標を週" + c.getNewValue() + "回に変更"
              : "トレーニング目的を" + goalModeLabel(c.getNewValue()) + "に変更";
      entries.add(
          new Entry(
              c.getChangedAt(),
              c.getId() == null ? 0 : c.getId(),
              new HistoryRow(c.getChangedAt().toLocalDate().format(MD), content, "会員が変更")));
    }
    entries.sort(Comparator.comparing(Entry::at).thenComparing(Entry::tiebreak).reversed());
    return entries.stream().map(Entry::row).toList();
  }

  /** 会員のトップ（Web・アプリ）に出す返事待ちの提案。無ければempty。 */
  @Transactional(readOnly = true)
  public Optional<MemberProposalView> pendingForMember(Long memberId) {
    Optional<GoalProposal> pending =
        goalProposalDao.selectProposalsByMember(memberId).stream()
            .filter(p -> GoalProposalStatus.PENDING.name().equals(p.getStatus()))
            .findFirst();
    if (pending.isEmpty()) return Optional.empty();
    GoalProposal p = pending.get();
    User member = userDao.selectById(memberId.intValue());
    Optional<Integer> manual = frequencyGoalService.findManualTarget(memberId);
    return Optional.of(
        new MemberProposalView(
            p.getId(),
            p.getProposerName(),
            manual.map(n -> "週" + n + "回").orElse("未設定"),
            "週" + p.getWeeklyTargetCount() + "回",
            goalModeLabel(member == null ? null : member.getCurrentGoalMode()),
            goalModeLabel(p.getGoalMode()),
            p.getComment(),
            p.getNextReviewDate(),
            p.getNextReviewDate().format(MD)));
  }

  /** プロフィールの「担当トレーナーと決めた目標」。今の目標が承認した提案と違えばempty。 */
  @Transactional(readOnly = true)
  public Optional<AgreementView> agreementFor(Long memberId) {
    User member = userDao.selectById(memberId.intValue());
    if (member == null) return Optional.empty();
    return agreedProposal(
            goalProposalDao.selectProposalsByMember(memberId),
            goalProposalDao.selectChangesByMember(memberId),
            frequencyGoalService.findManualTarget(memberId),
            GoalMode.fromString(member.getCurrentGoalMode()).name())
        .map(
            p ->
                new AgreementView(
                    "担当トレーナーと決めた目標（"
                        + p.getRespondedAt().toLocalDate().format(MD)
                        + nameSuffix(p.getProposerName())
                        + "）",
                    p.getNextReviewDate(),
                    p.getNextReviewDate().format(MD)));
  }

  /**
   * 今の目標が、最後に承認した提案のままか。承認より後に会員が自分で変えていたら外す（Q20-1）。値も念のため比べる。
   *
   * @param proposals 新しい順
   */
  static Optional<GoalProposal> agreedProposal(
      List<GoalProposal> proposals,
      List<MemberGoalChange> changes,
      Optional<Integer> manualTarget,
      String goalMode) {
    Optional<GoalProposal> accepted =
        proposals.stream()
            .filter(p -> GoalProposalStatus.ACCEPTED.name().equals(p.getStatus()))
            .filter(p -> p.getRespondedAt() != null)
            .findFirst();
    if (accepted.isEmpty()) return Optional.empty();
    GoalProposal p = accepted.get();
    boolean changedAfter =
        changes.stream().anyMatch(c -> !c.getChangedAt().isBefore(p.getRespondedAt()));
    if (changedAfter) return Optional.empty();
    if (manualTarget.isEmpty() || !manualTarget.get().equals(p.getWeeklyTargetCount())) {
      return Optional.empty();
    }
    if (!p.getGoalMode().equals(goalMode)) return Optional.empty();
    return accepted;
  }

  /** 担当会員の一覧（#12）の「目標の見直し」列。提案を受けたことが無い会員は含まない。 */
  @Transactional(readOnly = true)
  public Map<Long, ReviewStatus> reviewStatuses(Collection<Long> memberIds, LocalDate today) {
    Map<Long, ReviewStatus> result = new HashMap<>();
    if (memberIds.isEmpty()) return result;
    for (GoalProposal p :
        goalProposalDao.selectLatestProposalsByMemberIds(memberIds.stream().distinct().toList())) {
      // 見直し日の当日から、新しい提案を送るまで出し続ける（見送られても消さない。Q20-2 A）
      result.put(
          p.getMemberUserId(),
          new ReviewStatus(p.getNextReviewDate(), !p.getNextReviewDate().isAfter(today)));
    }
    return result;
  }

  // ===================== 提案・返事 =====================

  /**
   * 目標を提案する。返事待ちの前の提案は取り下げにする。
   *
   * @throws ResponseStatusException 提案できない会員は403
   * @throws IllegalArgumentException 入力が範囲外の場合
   */
  @Transactional
  public Long propose(
      User viewer,
      Long memberId,
      Integer weeklyTargetCount,
      String goalMode,
      String comment,
      LocalDate nextReviewDate,
      LocalDate today,
      LocalDateTime now) {
    User member = memberId == null ? null : userDao.selectById(memberId.intValue());
    requireCanPropose(viewer, member);
    if (weeklyTargetCount == null
        || weeklyTargetCount < FrequencyGoalService.MIN_TARGET
        || weeklyTargetCount > FrequencyGoalService.MAX_TARGET) {
      throw new IllegalArgumentException("週の回数目標は1〜7回の範囲で選んでください");
    }
    GoalMode mode = parseGoalMode(goalMode);
    String trimmed = comment == null ? null : comment.strip();
    if (trimmed != null && trimmed.isEmpty()) trimmed = null;
    if (trimmed != null && trimmed.codePointCount(0, trimmed.length()) > COMMENT_MAX_LENGTH) {
      throw new IllegalArgumentException("ひとことは" + COMMENT_MAX_LENGTH + "文字以内で入力してください");
    }
    if (nextReviewDate == null) {
      throw new IllegalArgumentException("次の見直し日を選んでください");
    }
    if (!nextReviewDate.isAfter(today)) {
      throw new IllegalArgumentException("次の見直し日は明日以降の日付を選んでください");
    }
    if (nextReviewDate.isAfter(today.plusDays(MAX_REVIEW_DAYS))) {
      throw new IllegalArgumentException("次の見直し日は1年以内の日付を選んでください");
    }

    goalProposalDao.withdrawPendingByMember(memberId, now);
    GoalProposal p = new GoalProposal();
    p.setMemberUserId(memberId);
    p.setProposerUserId(viewer.getUserId().longValue());
    p.setWeeklyTargetCount(weeklyTargetCount);
    p.setGoalMode(mode.name());
    p.setComment(trimmed);
    p.setNextReviewDate(nextReviewDate);
    p.setStatus(GoalProposalStatus.PENDING.name());
    p.setProposedAt(now);
    goalProposalDao.insertProposal(p);
    return p.getId();
  }

  /**
   * 会員が提案に返事をする。本人宛ての返事待ちの提案だけ受け付ける。
   *
   * @param accept true=この目標にする、false=今回は見送る
   * @throws ResponseStatusException 本人宛てでなければ404、返事待ちでなければ409
   */
  @Transactional
  public void respond(
      Long memberId, Long proposalId, boolean accept, LocalDate today, LocalDateTime now) {
    GoalProposal p =
        goalProposalDao
            .selectProposalById(proposalId)
            .filter(x -> x.getMemberUserId().equals(memberId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "提案が見つかりません"));
    if (!GoalProposalStatus.PENDING.name().equals(p.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, alreadyRespondedMessage(p));
    }
    GoalProposalStatus status = accept ? GoalProposalStatus.ACCEPTED : GoalProposalStatus.DECLINED;
    // 同時に新しい提案が届いて取り下げになった場合などは更新されない
    if (goalProposalDao.markResponded(p.getId(), status.name(), now) == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "この提案はすでに返事済みか、取り下げられました");
    }
    if (accept) {
      frequencyGoalService.applyAgreedGoal(memberId, p.getWeeklyTargetCount(), today);
      profileService.updateGoalMode(memberId.intValue(), p.getGoalMode());
    }
  }

  private static String alreadyRespondedMessage(GoalProposal p) {
    return GoalProposalStatus.WITHDRAWN.name().equals(p.getStatus())
        ? "この提案は取り下げられました。新しい提案を確認してください"
        : "この提案はすでに返事済みです";
  }

  // ===================== 会員が自分で変える =====================

  /** 会員が自分で週の回数目標を変える（#7の保存と同じ）。値が変わったときだけ「会員が変更」を記録する。 */
  @Transactional
  public void updateWeeklyTargetByMember(
      Long userId, int weeklyTargetCount, LocalDate today, LocalDateTime now) {
    Optional<Integer> before = frequencyGoalService.findManualTarget(userId);
    frequencyGoalService.updateGoal(userId, weeklyTargetCount, today);
    if (before.isEmpty() || before.get() != weeklyTargetCount) {
      recordChange(
          userId,
          MemberGoalChange.WEEKLY_TARGET,
          before.map(String::valueOf).orElse(null),
          String.valueOf(weeklyTargetCount),
          now);
    }
  }

  /** 会員が自分で目的モードを変える。値が変わったときだけ「会員が変更」を記録する。 */
  @Transactional
  public void updateGoalModeByMember(Integer userId, String goalMode, LocalDateTime now) {
    String mode = GoalMode.fromString(goalMode).name();
    User user = userDao.selectById(userId);
    String before = user == null ? null : GoalMode.fromString(user.getCurrentGoalMode()).name();
    profileService.updateGoalMode(userId, mode);
    if (!mode.equals(before)) {
      recordChange(userId.longValue(), MemberGoalChange.GOAL_MODE, before, mode, now);
    }
  }

  private void recordChange(
      Long userId, String type, String oldValue, String newValue, LocalDateTime now) {
    MemberGoalChange c = new MemberGoalChange();
    c.setMemberUserId(userId);
    c.setChangeType(type);
    c.setOldValue(oldValue);
    c.setNewValue(newValue);
    c.setChangedAt(now);
    goalProposalDao.insertChange(c);
  }

  // ===================== 共通 =====================

  private void requireCanPropose(User viewer, User member) {
    if (!canPropose(viewer, member)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この会員に目標を提案する権限がありません");
    }
  }

  private static GoalMode parseGoalMode(String value) {
    if (value == null) throw new IllegalArgumentException("トレーニング目的を選んでください");
    try {
      return GoalMode.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("トレーニング目的を選んでください");
    }
  }

  private static String nameSuffix(String name) {
    return name == null || name.isBlank() ? "" : " " + name;
  }

  /** 目的モードの表示名（プロフィールの選択肢と同じ）。 */
  public static String goalModeLabel(String goalMode) {
    return switch (GoalMode.fromString(goalMode)) {
      case BULKING -> "筋肥大";
      case CUTTING -> "減量";
      case MAINTENANCE -> "維持";
    };
  }
}
