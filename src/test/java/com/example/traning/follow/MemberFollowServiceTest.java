package com.example.traning.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.audit.AuditLogService;
import com.example.traning.dao.UserDao;
import com.example.traning.follow.MemberFollowService.ListView;
import com.example.traning.follow.MemberFollowService.MemberRow;
import com.example.traning.follow.MemberFollowService.ScopeOption;
import com.example.traning.follow.MemberFollowService.Sort;
import com.example.traning.follow.MemberFollowService.Tab;
import com.example.traning.goal.FrequencyGoalService;
import com.example.traning.goal.GoalProposal;
import com.example.traning.goal.GoalProposalDao;
import com.example.traning.goal.GoalProposalService;
import com.example.traning.health.HealthInfoAccessPolicy;
import com.example.traning.organization.GymSettingService;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.smarttrainer.autogen.UserExcludedItem;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.UserTrainingDate;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 機能見直し-1-#12: 担当会員の一覧の範囲（トレーナー・店舗管理者・ジム管理者・システム管理者、範囲外は403）、
 * 「対応済みにする」を押せる人、痛みの報告の理由の文言のジムの設定による出し分け。
 *
 * <p>範囲の判定は実物の {@link OrganizationScopeResolver}・{@link MemberAccessPolicy}・{@link
 * HealthInfoAccessPolicy}を使い、DAOだけを差し替える。
 */
@ExtendWith(MockitoExtension.class)
class MemberFollowServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

  // ジム10（店舗11・12）、ジム20（店舗21）
  private static final long GYM_10 = 10L;
  private static final long STORE_11 = 11L;
  private static final long STORE_12 = 12L;
  private static final long GYM_20 = 20L;
  private static final long STORE_21 = 21L;

  private static final User TRAINER = staff(100, "ROLE_TRAINER", STORE_11, "山本 健");
  private static final User STORE_ADMIN = staff(101, "ROLE_STORE_ADMIN", STORE_11, "店舗 管理");
  private static final User ORG_ADMIN = staff(102, "ROLE_ORG_ADMIN", GYM_10, "ジム 管理");
  private static final User ADMIN = staff(103, "ROLE_ADMIN", null, "システム 管理");
  private static final User OTHER_TRAINER = staff(105, "ROLE_TRAINER", STORE_12, "佐々木 舞");

  @Mock private MemberFollowDao memberFollowDao;
  @Mock private UserDao userDao;
  @Mock private TrainingDao trainingDao;
  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;
  @Mock private GymSettingService gymSettingService;
  @Mock private AuditLogService auditLogService;
  @Mock private FrequencyGoalService frequencyGoalService;
  @Mock private PeriodizationService periodizationService;
  @Mock private GoalProposalDao goalProposalDao;
  @Mock private ProfileService profileService;

  private MemberFollowService service;
  private final List<User> members = new ArrayList<>();
  private final Map<Long, LocalDate> lastTraining = new HashMap<>();
  private final List<UserExcludedItem> exclusions = new ArrayList<>();
  private final List<GoalProposal> latestProposals = new ArrayList<>();

  @BeforeEach
  void setUp() {
    Map<Long, Organization> orgs = new HashMap<>();
    orgs.put(GYM_10, org(GYM_10, "GYM", null, "本部ジム"));
    orgs.put(STORE_11, org(STORE_11, "STORE", GYM_10, "渋谷店"));
    orgs.put(STORE_12, org(STORE_12, "STORE", GYM_10, "新宿店"));
    orgs.put(GYM_20, org(GYM_20, "GYM", null, "別ジム"));
    orgs.put(STORE_21, org(STORE_21, "STORE", GYM_20, "梅田店"));
    lenient()
        .when(organizationDao.selectById(anyLong()))
        .thenAnswer(inv -> Optional.ofNullable(orgs.get((Long) inv.getArgument(0))));
    lenient()
        .when(organizationDao.selectByParentOrganizationId(anyLong()))
        .thenAnswer(
            inv ->
                orgs.values().stream()
                    .filter(o -> inv.getArgument(0).equals(o.getParentOrganizationId()))
                    .sorted(java.util.Comparator.comparing(Organization::getId))
                    .toList());
    lenient()
        .when(organizationDao.selectAll())
        .thenAnswer(
            inv ->
                orgs.values().stream()
                    .sorted(java.util.Comparator.comparing(Organization::getId))
                    .toList());
    lenient()
        .when(organizationDao.selectByIds(any()))
        .thenAnswer(inv -> ((List<Long>) inv.getArgument(0)).stream().map(orgs::get).toList());
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(anyLong()))
        .thenReturn(List.of());

    lenient()
        .when(memberFollowDao.selectMembersByOrganizationIds(any()))
        .thenAnswer(
            inv ->
                members.stream()
                    .filter(m -> ((List<Long>) inv.getArgument(0)).contains(m.getOrganizationId()))
                    .toList());
    lenient()
        .when(memberFollowDao.selectMembersByAssignedTrainerId(anyLong()))
        .thenAnswer(
            inv ->
                members.stream()
                    .filter(m -> inv.getArgument(0).equals(m.getAssignedTrainerId()))
                    .toList());
    lenient()
        .when(memberFollowDao.selectUsersByIds(any()))
        .thenReturn(List.of(TRAINER, STORE_ADMIN, ORG_ADMIN, OTHER_TRAINER));
    lenient()
        .when(memberFollowDao.selectReasonedExclusionsSince(any(), any()))
        .thenAnswer(inv -> exclusions);
    lenient()
        .when(memberFollowDao.selectLatestAcknowledgementsByMemberIds(any()))
        .thenReturn(List.of());
    lenient()
        .when(trainingDao.selectLastTrainingDatesByUserIds(any(), any()))
        .thenAnswer(
            inv ->
                lastTraining.entrySet().stream()
                    .map(
                        e -> {
                          UserTrainingDate d = new UserTrainingDate();
                          d.setUserId(e.getKey());
                          d.setTrainingDate(e.getValue());
                          return d;
                        })
                    .toList());
    lenient().when(frequencyGoalService.getStatusesForMembers(any(), any())).thenReturn(Map.of());
    lenient().when(periodizationService.findMembersInDeloadWeek(any(), any())).thenReturn(Set.of());
    lenient()
        .when(goalProposalDao.selectLatestProposalsByMemberIds(any()))
        .thenAnswer(
            inv ->
                latestProposals.stream()
                    .filter(p -> ((List<Long>) inv.getArgument(0)).contains(p.getMemberUserId()))
                    .toList());

    OrganizationScopeResolver resolver =
        new OrganizationScopeResolver(organizationDao, userStoreAccessDao);
    service =
        new MemberFollowService(
            memberFollowDao,
            userDao,
            trainingDao,
            organizationDao,
            resolver,
            new MemberAccessPolicy(resolver),
            new HealthInfoAccessPolicy(resolver, gymSettingService, auditLogService),
            frequencyGoalService,
            periodizationService,
            new GoalProposalService(
                goalProposalDao,
                userDao,
                new MemberAccessPolicy(resolver),
                frequencyGoalService,
                profileService));
  }

  // ===================== 自分の担当 =====================

  @Test
  void トレーナーの一覧は自分の担当会員だけで印の付いた行は対応済みにできる() {
    User m1 = member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, null, "中島 七海");
    member(3, STORE_11, OTHER_TRAINER, "田中 一郎");
    lastTraining.put(1L, TODAY.minusDays(18));

    ListView view = service.list(TRAINER, null, null, null, TODAY);

    assertThat(view.tabs()).containsExactly(Tab.MINE);
    assertThat(view.rows()).extracting(MemberRow::memberId).containsExactly(1L);
    MemberRow row = view.rows().get(0);
    assertThat(row.follow().reasons()).containsExactly("最後の記録から18日");
    assertThat(row.canAcknowledge()).isTrue();
    assertThat(row.goalLabel()).isEqualTo("目標なし");
    assertThat(row.thisWeekLabel()).isEqualTo("—");
    assertThat(m1.getUserName()).isEqualTo(row.name());
  }

  @Test
  void トレーナーは店舗全体を開けない() {
    assertForbidden(() -> service.list(TRAINER, Tab.STORE, null, null, TODAY));
  }

  @Test
  void システム管理者は担当トレーナーになれないため自分の担当のタブは無い() {
    assertThat(MemberFollowService.availableTabs(ADMIN)).containsExactly(Tab.STORE);
    assertForbidden(() -> service.list(ADMIN, Tab.MINE, null, null, TODAY));
    assertThat(service.countFlaggedAssigned(ADMIN, TODAY)).isZero();
  }

  @Test
  void トップの人数は自分の担当で印が付いている人数() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, TRAINER, "鈴木 次郎");
    member(3, STORE_11, OTHER_TRAINER, "田中 一郎");
    lastTraining.put(1L, TODAY.minusDays(20));
    lastTraining.put(2L, TODAY.minusDays(1));
    lastTraining.put(3L, TODAY.minusDays(30));

    assertThat(service.countFlaggedAssigned(TRAINER, TODAY)).isEqualTo(1);
  }

  // ===================== 店舗全体の範囲 =====================

  @Test
  void 店舗管理者の店舗全体は自分の店舗で担当の列には未割当も出る() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, null, "中島 七海");
    member(3, STORE_12, OTHER_TRAINER, "田中 一郎");

    ListView view = service.list(STORE_ADMIN, Tab.STORE, null, Sort.TRAINER, TODAY);

    assertThat(view.scopeOptions()).extracting(ScopeOption::value).containsExactly("store:11");
    assertThat(view.selectedScope()).isEqualTo("store:11");
    // 担当者順: 未割当は最後
    assertThat(view.rows()).extracting(MemberRow::trainerName).containsExactly("山本 健", null);
  }

  @Test
  void 店舗管理者は他の店舗やジム全体を指定すると403() {
    assertForbidden(() -> service.list(STORE_ADMIN, Tab.STORE, "store:12", null, TODAY));
    assertForbidden(() -> service.list(STORE_ADMIN, Tab.STORE, "gym:10", null, TODAY));
    assertForbidden(() -> service.list(STORE_ADMIN, Tab.STORE, "store:abc", null, TODAY));
  }

  @Test
  void 店舗管理者は兼任の店舗も選べる() {
    when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(101L)).thenReturn(List.of(STORE_12));
    member(3, STORE_12, OTHER_TRAINER, "田中 一郎");

    ListView view = service.list(STORE_ADMIN, Tab.STORE, "store:12", null, TODAY);

    assertThat(view.scopeOptions())
        .extracting(ScopeOption::value)
        .containsExactly("store:11", "store:12");
    assertThat(view.rows()).extracting(MemberRow::memberId).containsExactly(3L);
  }

  @Test
  void ジム管理者はジム全体と自分のジムの店舗を選べ他のジムは403() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(3, STORE_12, OTHER_TRAINER, "田中 一郎");
    member(4, STORE_21, null, "大阪 太郎");

    ListView view = service.list(ORG_ADMIN, Tab.STORE, null, null, TODAY);

    assertThat(view.scopeOptions())
        .extracting(ScopeOption::label)
        .containsExactly("ジム全体", "渋谷店", "新宿店");
    assertThat(view.selectedScope()).isEqualTo("gym:10");
    assertThat(view.rows()).extracting(MemberRow::memberId).containsExactlyInAnyOrder(1L, 3L);
    assertForbidden(() -> service.list(ORG_ADMIN, Tab.STORE, "gym:20", null, TODAY));
    assertForbidden(() -> service.list(ORG_ADMIN, Tab.STORE, "store:21", null, TODAY));
  }

  @Test
  void システム管理者はすべてのジムの店舗とジム全体を選べる() {
    member(4, STORE_21, null, "大阪 太郎");

    ListView view = service.list(ADMIN, null, "gym:20", null, TODAY);

    assertThat(view.tab()).isEqualTo(Tab.STORE);
    assertThat(view.scopeOptions())
        .extracting(ScopeOption::value)
        .containsExactly("gym:10", "store:11", "store:12", "gym:20", "store:21");
    assertThat(view.rows()).extracting(MemberRow::memberId).containsExactly(4L);
  }

  // ===================== 痛みの報告の文言 =====================

  @Test
  void 管理者にはジムの設定が見られないなら理由を出さず監査ログも残さない() {
    member(1, STORE_11, TRAINER, "高橋 三郎");
    lastTraining.put(1L, TODAY.minusDays(1));
    pain(1, "ショルダープレス", "右肩に違和感");
    lenient().when(gymSettingService.isStaffHealthInfoVisible(STORE_11)).thenReturn(false);

    MemberRow row = service.list(STORE_ADMIN, Tab.STORE, null, null, TODAY).rows().get(0);

    assertThat(row.follow().reasons()).containsExactly("痛みの報告（除外を追加）");
    verify(auditLogService, never())
        .record(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  void 管理者にはジムの設定が見られるなら理由を出し閲覧を監査ログに残す() {
    member(1, STORE_11, TRAINER, "高橋 三郎");
    lastTraining.put(1L, TODAY.minusDays(1));
    pain(1, "ショルダープレス", "右肩に違和感");
    when(gymSettingService.isStaffHealthInfoVisible(STORE_11)).thenReturn(true);

    MemberRow row = service.list(STORE_ADMIN, Tab.STORE, null, null, TODAY).rows().get(0);

    assertThat(row.follow().reasons()).containsExactly("痛みの報告（ショルダープレスを除外、理由「右肩に違和感」）");
    verify(auditLogService)
        .record(
            eq(101L),
            eq(HealthInfoAccessPolicy.AUDIT_ACTION),
            eq("user_excluded_items"),
            eq(1L),
            any(),
            any());
  }

  @Test
  void 担当トレーナーにはジムの設定によらず理由を出す() {
    member(1, STORE_11, TRAINER, "高橋 三郎");
    lastTraining.put(1L, TODAY.minusDays(1));
    pain(1, "ショルダープレス", "右肩に違和感");

    MemberRow row = service.list(TRAINER, null, null, null, TODAY).rows().get(0);

    assertThat(row.follow().reasons()).containsExactly("痛みの報告（ショルダープレスを除外、理由「右肩に違和感」）");
    verify(gymSettingService, never()).isStaffHealthInfoVisible(anyLong());
  }

  // ===================== 対応済みにする =====================

  @Test
  void 担当トレーナーが対応済みにすると押した時点の理由の種類と押した人を残す() {
    User m1 = member(1, STORE_11, TRAINER, "佐藤 花子");
    lastTraining.put(1L, TODAY.minusDays(18));
    pain(1, "スクワット", "膝");
    when(userDao.selectById(1)).thenReturn(m1);
    LocalDateTime now = TODAY.atTime(15, 0);

    service.acknowledge(TRAINER, 1L, TODAY, now);

    ArgumentCaptor<MemberFollowAcknowledgement> captor =
        ArgumentCaptor.forClass(MemberFollowAcknowledgement.class);
    verify(memberFollowDao).insertAcknowledgement(captor.capture());
    assertThat(captor.getValue().getMemberUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getReasonTypes()).isEqualTo("NO_RECENT_RECORD,PAIN_REPORT");
    assertThat(captor.getValue().getActedBy()).isEqualTo(100L);
    assertThat(captor.getValue().getActedAt()).isEqualTo(now);
  }

  @Test
  void トレーナーは担当外の会員を対応済みにできない() {
    User m2 = member(2, STORE_11, OTHER_TRAINER, "鈴木 次郎");
    lastTraining.put(2L, TODAY.minusDays(30));
    when(userDao.selectById(2)).thenReturn(m2);

    assertForbidden(() -> service.acknowledge(TRAINER, 2L, TODAY, TODAY.atTime(9, 0)));
    verify(memberFollowDao, never()).insertAcknowledgement(any());
  }

  @Test
  void 範囲内の管理者は担当でなくても対応済みにでき範囲外の管理者はできない() {
    User m1 = member(1, STORE_11, TRAINER, "佐藤 花子");
    lastTraining.put(1L, TODAY.minusDays(30));
    when(userDao.selectById(1)).thenReturn(m1);
    User otherGymAdmin = staff(104, "ROLE_ORG_ADMIN", GYM_20, "別 管理");

    assertForbidden(() -> service.acknowledge(otherGymAdmin, 1L, TODAY, TODAY.atTime(9, 0)));
    service.acknowledge(STORE_ADMIN, 1L, TODAY, TODAY.atTime(9, 0));

    verify(memberFollowDao).insertAcknowledgement(any());
  }

  @Test
  void 印が付いていない会員は対応済みにできない() {
    User m1 = member(1, STORE_11, TRAINER, "佐藤 花子");
    lastTraining.put(1L, TODAY.minusDays(1));
    when(userDao.selectById(1)).thenReturn(m1);

    assertThatThrownBy(() -> service.acknowledge(TRAINER, 1L, TODAY, TODAY.atTime(9, 0)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(memberFollowDao, never()).insertAcknowledgement(any());
  }

  @Test
  void スタッフの行は対応済みにできない() {
    when(userDao.selectById(105)).thenReturn(OTHER_TRAINER);
    assertForbidden(() -> service.acknowledge(STORE_ADMIN, 105L, TODAY, TODAY.atTime(9, 0)));
  }

  // ===================== 並べ替え =====================

  @Test
  void フォロー推奨が先の並びは印のある会員を先にし記録が無い会員を最も古い扱いにする() {
    member(1, STORE_11, TRAINER, "あ 一");
    member(2, STORE_11, TRAINER, "い 二");
    member(3, STORE_11, TRAINER, "う 三");
    lastTraining.put(1L, TODAY.minusDays(1));
    lastTraining.put(2L, TODAY.minusDays(20));
    lastTraining.put(3L, TODAY.minusDays(5));

    assertThat(service.list(TRAINER, null, null, Sort.FLAG, TODAY).rows())
        .extracting(MemberRow::memberId)
        .containsExactly(2L, 3L, 1L);
    assertThat(service.list(TRAINER, null, null, Sort.NAME, TODAY).rows())
        .extracting(MemberRow::memberId)
        .containsExactly(1L, 2L, 3L);
  }

  @Test
  void 担当者順は自分の担当では使えずフォロー推奨が先になる() {
    assertThat(service.list(TRAINER, null, null, Sort.TRAINER, TODAY).sort()).isEqualTo(Sort.FLAG);
  }

  // ===================== helpers =====================

  private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable c) {
    assertThatThrownBy(c)
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  private void latestProposal(long memberId, LocalDate nextReviewDate, String status) {
    GoalProposal p = new GoalProposal();
    p.setId((long) (latestProposals.size() + 1));
    p.setMemberUserId(memberId);
    p.setNextReviewDate(nextReviewDate);
    p.setStatus(status);
    latestProposals.add(p);
  }

  // ===================== 機能見直し-1-#20 目標の見直し =====================

  @Test
  void 見直し日の当日以降は青い印を出し見送られても消さず先の日付と未設定は印なし() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, TRAINER, "田中 一郎");
    member(3, STORE_11, TRAINER, "鈴木 次郎");
    member(4, STORE_11, TRAINER, "中島 七海");
    latestProposal(1, TODAY, "ACCEPTED");
    latestProposal(2, TODAY.plusDays(30), "PENDING");
    latestProposal(4, TODAY.minusDays(2), "DECLINED");
    for (long id = 1; id <= 4; id++) lastTraining.put(id, TODAY.minusDays(1));

    Map<Long, MemberRow> rows = new HashMap<>();
    service
        .list(TRAINER, null, null, Sort.NAME, TODAY)
        .rows()
        .forEach(r -> rows.put(r.memberId(), r));

    assertThat(rows.get(1L).reviewDue()).isTrue();
    assertThat(rows.get(1L).reviewDateLabel()).isEqualTo("11/19（今日）");
    assertThat(rows.get(1L).canPropose()).isTrue();
    assertThat(rows.get(2L).reviewDue()).isFalse();
    assertThat(rows.get(2L).reviewDateLabel()).isEqualTo("12/19");
    assertThat(rows.get(3L).reviewDue()).isFalse();
    assertThat(rows.get(3L).nextReviewDate()).isNull();
    assertThat(rows.get(4L).reviewDue()).isTrue();
    assertThat(rows.get(4L).reviewDateLabel()).isEqualTo("11/17（2日前）");
  }

  @Test
  void フォロー推奨が先の並びは印のある会員の次に見直し日の会員を出す() {
    member(1, STORE_11, TRAINER, "あ 見直し");
    member(2, STORE_11, TRAINER, "い 印あり");
    member(3, STORE_11, TRAINER, "う その他");
    lastTraining.put(1L, TODAY.minusDays(1));
    lastTraining.put(2L, TODAY.minusDays(20));
    lastTraining.put(3L, TODAY.minusDays(5));
    latestProposal(1, TODAY.minusDays(1), "ACCEPTED");

    ListView view = service.list(TRAINER, null, null, null, TODAY);

    assertThat(view.rows()).extracting(MemberRow::memberId).containsExactly(2L, 1L, 3L);
  }

  @Test
  void トップの見直しの人数は自分の担当で見直し日の印が付いている人数() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, TRAINER, "鈴木 次郎");
    member(3, STORE_11, OTHER_TRAINER, "田中 一郎");
    latestProposal(1, TODAY, "DECLINED");
    latestProposal(2, TODAY.plusDays(1), "PENDING");
    latestProposal(3, TODAY.minusDays(3), "ACCEPTED");

    assertThat(service.countReviewDueAssigned(TRAINER, TODAY)).isEqualTo(1);
    assertThat(service.countReviewDueAssigned(OTHER_TRAINER, TODAY)).isEqualTo(1);
    assertThat(service.countReviewDueAssigned(ADMIN, TODAY)).isZero();
  }

  @Test
  void 店舗全体では範囲内の管理者は担当でなくても目標を提案できる() {
    member(1, STORE_11, TRAINER, "佐藤 花子");
    member(2, STORE_11, null, "中島 七海");
    latestProposal(1, TODAY, "ACCEPTED");

    ListView view = service.list(STORE_ADMIN, Tab.STORE, null, Sort.NAME, TODAY);

    assertThat(view.rows()).allMatch(MemberRow::canPropose);
  }

  private User member(int id, long orgId, User trainer, String name) {
    User u =
        User.builder()
            .userId(id)
            .userName(name)
            .role("ROLE_USER")
            .organizationId(orgId)
            .assignedTrainerId(trainer == null ? null : trainer.getUserId().longValue())
            .createDatetime(TODAY.minusDays(100).atStartOfDay())
            .build();
    members.add(u);
    return u;
  }

  private void pain(long userId, String item, String reason) {
    UserExcludedItem e = new UserExcludedItem();
    e.setUserId(userId);
    e.setItemName(item);
    e.setReason(reason);
    e.setCreatedAt(TODAY.minusDays(1).atTime(9, 0));
    exclusions.add(e);
  }

  private static User staff(int id, String role, Long orgId, String name) {
    return User.builder().userId(id).userName(name).role(role).organizationId(orgId).build();
  }

  private static Organization org(long id, String type, Long parent, String name) {
    Organization o = new Organization();
    o.setId(id);
    o.setType(type);
    o.setParentOrganizationId(parent);
    o.setName(name);
    return o;
  }
}
