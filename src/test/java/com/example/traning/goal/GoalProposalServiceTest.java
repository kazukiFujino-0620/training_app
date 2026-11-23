package com.example.traning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.goal.GoalProposalService.AgreementView;
import com.example.traning.goal.GoalProposalService.HistoryRow;
import com.example.traning.goal.GoalProposalService.MemberProposalView;
import com.example.traning.goal.GoalProposalService.ReviewStatus;
import com.example.traning.goal.GoalProposalService.StaffGoalView;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 機能見直し-1-#20: 目標の提案（提案できる人・取り下げ・承認・見送り）、会員が自分で変えた記録、履歴、 「担当トレーナーと決めた目標」の表示、見直し日の印。
 *
 * <p>提案できる人の判定は実物の {@link MemberAccessPolicy}・{@link OrganizationScopeResolver}を使い、DAOだけを差し替える。
 */
@ExtendWith(MockitoExtension.class)
class GoalProposalServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
  private static final LocalDateTime NOW = TODAY.atTime(10, 0);

  // ジム10（店舗11・12）、ジム20（店舗21）
  private static final long GYM_10 = 10L;
  private static final long STORE_11 = 11L;
  private static final long STORE_12 = 12L;
  private static final long STORE_21 = 21L;

  private static final User TRAINER = staff(100, "ROLE_TRAINER", STORE_11, "山本 健");
  private static final User OTHER_TRAINER = staff(105, "ROLE_TRAINER", STORE_11, "佐々木 舞");
  private static final User STORE_ADMIN = staff(101, "ROLE_STORE_ADMIN", STORE_11, "店舗 管理");
  private static final User OTHER_STORE_ADMIN = staff(106, "ROLE_STORE_ADMIN", STORE_12, "別店舗 管理");
  private static final User ORG_ADMIN = staff(102, "ROLE_ORG_ADMIN", GYM_10, "ジム 管理");
  private static final User OTHER_ORG_ADMIN = staff(107, "ROLE_ORG_ADMIN", 20L, "別ジム 管理");
  private static final User ADMIN = staff(103, "ROLE_ADMIN", null, "システム 管理");

  @Mock private GoalProposalDao goalProposalDao;
  @Mock private UserDao userDao;
  @Mock private FrequencyGoalService frequencyGoalService;
  @Mock private ProfileService profileService;
  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;

  private GoalProposalService service;
  private User member;
  private final List<GoalProposal> proposals = new ArrayList<>();
  private final List<MemberGoalChange> changes = new ArrayList<>();

  @BeforeEach
  void setUp() {
    OrganizationScopeResolver resolver =
        new OrganizationScopeResolver(organizationDao, userStoreAccessDao);
    service =
        new GoalProposalService(
            goalProposalDao,
            userDao,
            new MemberAccessPolicy(resolver),
            frequencyGoalService,
            profileService);
    member =
        User.builder()
            .userId(1)
            .userName("佐藤 花子")
            .role("ROLE_USER")
            .organizationId(STORE_11)
            .assignedTrainerId(100L)
            .currentGoalMode("MAINTENANCE")
            .build();
    lenient().when(userDao.selectById(1)).thenReturn(member);
    Organization s11 = new Organization();
    s11.setId(STORE_11);
    Organization s12 = new Organization();
    s12.setId(STORE_12);
    lenient()
        .when(organizationDao.selectByParentOrganizationId(GYM_10))
        .thenReturn(List.of(s11, s12));
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(anyLong()))
        .thenReturn(List.of());
    lenient().when(goalProposalDao.selectProposalsByMember(1L)).thenReturn(proposals);
    lenient().when(goalProposalDao.selectChangesByMember(1L)).thenReturn(changes);
  }

  // ===================== 提案できる人 =====================

  @Test
  void 担当トレーナーと範囲内の管理者は提案でき担当外のトレーナーと範囲外の管理者はできない() {
    assertThat(service.canPropose(TRAINER, member)).isTrue();
    assertThat(service.canPropose(STORE_ADMIN, member)).isTrue();
    assertThat(service.canPropose(ORG_ADMIN, member)).isTrue();
    // システム管理者は担当トレーナーになれないが、範囲内の管理者として扱う（#12の対応済みと同じ）
    assertThat(service.canPropose(ADMIN, member)).isTrue();

    assertThat(service.canPropose(OTHER_TRAINER, member)).isFalse();
    assertThat(service.canPropose(OTHER_STORE_ADMIN, member)).isFalse();
    assertThat(service.canPropose(OTHER_ORG_ADMIN, member)).isFalse();
    // 会員本人やスタッフ宛ては不可
    assertThat(service.canPropose(member, member)).isFalse();
    assertThat(service.canPropose(STORE_ADMIN, OTHER_TRAINER)).isFalse();
  }

  @Test
  void 担当外のトレーナーが提案すると403で保存しない() {
    assertThatThrownBy(
            () ->
                service.propose(
                    OTHER_TRAINER, 1L, 3, "BULKING", null, TODAY.plusDays(28), TODAY, NOW))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(goalProposalDao, never()).insertProposal(any());
    verify(goalProposalDao, never()).withdrawPendingByMember(anyLong(), any());
  }

  @Test
  void 範囲外の管理者は会員詳細の目標のカードも見られない() {
    assertThatThrownBy(() -> service.staffView(OTHER_STORE_ADMIN, member, TODAY))
        .isInstanceOf(ResponseStatusException.class);
  }

  // ===================== 提案 =====================

  @Test
  void 提案すると返事待ちの前の提案を取り下げてから返事待ちで保存する() {
    service.propose(
        TRAINER, 1L, 3, "BULKING", "  まずは週3回を4週続けましょう ", TODAY.plusDays(28), TODAY, NOW);

    InOrder order = inOrder(goalProposalDao);
    order.verify(goalProposalDao).withdrawPendingByMember(1L, NOW);
    ArgumentCaptor<GoalProposal> captor = ArgumentCaptor.forClass(GoalProposal.class);
    order.verify(goalProposalDao).insertProposal(captor.capture());
    GoalProposal p = captor.getValue();
    assertThat(p.getMemberUserId()).isEqualTo(1L);
    assertThat(p.getProposerUserId()).isEqualTo(100L);
    assertThat(p.getWeeklyTargetCount()).isEqualTo(3);
    assertThat(p.getGoalMode()).isEqualTo("BULKING");
    assertThat(p.getComment()).isEqualTo("まずは週3回を4週続けましょう");
    assertThat(p.getNextReviewDate()).isEqualTo(TODAY.plusDays(28));
    assertThat(p.getStatus()).isEqualTo("PENDING");
    assertThat(p.getProposedAt()).isEqualTo(NOW);
    // 提案だけでは会員の目標は変わらない
    verify(frequencyGoalService, never()).applyAgreedGoal(anyLong(), anyInt(), any());
    verify(profileService, never()).updateGoalMode(anyInt(), anyString());
  }

  @Test
  void ひとことは任意で空欄は保存せず50文字を超えると保存しない() {
    service.propose(TRAINER, 1L, 3, "CUTTING", "   ", TODAY.plusDays(1), TODAY, NOW);
    ArgumentCaptor<GoalProposal> captor = ArgumentCaptor.forClass(GoalProposal.class);
    verify(goalProposalDao).insertProposal(captor.capture());
    assertThat(captor.getValue().getComment()).isNull();

    service.propose(TRAINER, 1L, 3, "CUTTING", "あ".repeat(50), TODAY.plusDays(1), TODAY, NOW);
    assertThatThrownBy(
            () ->
                service.propose(
                    TRAINER, 1L, 3, "CUTTING", "あ".repeat(51), TODAY.plusDays(1), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ひとことは50文字以内で入力してください");
  }

  @Test
  void 週の回数目標と目的と見直し日の範囲外は保存しない() {
    assertThatThrownBy(
            () -> service.propose(TRAINER, 1L, 0, "BULKING", null, TODAY.plusDays(28), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.propose(TRAINER, 1L, 8, "BULKING", null, TODAY.plusDays(28), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.propose(TRAINER, 1L, 3, "WEIGHT", null, TODAY.plusDays(28), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.propose(TRAINER, 1L, 3, "BULKING", null, TODAY, TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("次の見直し日は明日以降の日付を選んでください");
    assertThatThrownBy(() -> service.propose(TRAINER, 1L, 3, "BULKING", null, null, TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.propose(TRAINER, 1L, 3, "BULKING", null, TODAY.plusDays(366), TODAY, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    verify(goalProposalDao, never()).insertProposal(any());
  }

  // ===================== 返事 =====================

  @Test
  void 承認すると週の回数目標を目標を設定した週として保存し目的モードも変える() {
    GoalProposal p = proposal(9L, 4, "BULKING", "PENDING", NOW.minusDays(1), null);
    when(goalProposalDao.selectProposalById(9L)).thenReturn(Optional.of(p));
    when(goalProposalDao.markResponded(9L, "ACCEPTED", NOW)).thenReturn(1);

    service.respond(1L, 9L, true, TODAY, NOW);

    verify(frequencyGoalService).applyAgreedGoal(1L, 4, TODAY);
    verify(profileService).updateGoalMode(1, "BULKING");
    // 承認は「会員が変更」として記録しない
    verify(goalProposalDao, never()).insertChange(any());
  }

  @Test
  void 見送ると目標は変えない() {
    GoalProposal p = proposal(9L, 4, "BULKING", "PENDING", NOW.minusDays(1), null);
    when(goalProposalDao.selectProposalById(9L)).thenReturn(Optional.of(p));
    when(goalProposalDao.markResponded(9L, "DECLINED", NOW)).thenReturn(1);

    service.respond(1L, 9L, false, TODAY, NOW);

    verify(frequencyGoalService, never()).applyAgreedGoal(anyLong(), anyInt(), any());
    verify(profileService, never()).updateGoalMode(anyInt(), anyString());
  }

  @Test
  void 他の会員宛ての提案には返事できず404() {
    GoalProposal p = proposal(9L, 4, "BULKING", "PENDING", NOW.minusDays(1), null);
    p.setMemberUserId(2L);
    when(goalProposalDao.selectProposalById(9L)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.respond(1L, 9L, true, TODAY, NOW))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    verify(goalProposalDao, never()).markResponded(anyLong(), anyString(), any());
  }

  @Test
  void 取り下げや返事済みの提案には返事できず409() {
    GoalProposal p = proposal(9L, 4, "BULKING", "WITHDRAWN", NOW.minusDays(1), NOW);
    when(goalProposalDao.selectProposalById(9L)).thenReturn(Optional.of(p));

    assertThatThrownBy(() -> service.respond(1L, 9L, true, TODAY, NOW))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("取り下げられました")
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    verify(frequencyGoalService, never()).applyAgreedGoal(anyLong(), anyInt(), any());
  }

  @Test
  void 同時に取り下げになって更新できなかった場合は目標を変えず409() {
    GoalProposal p = proposal(9L, 4, "BULKING", "PENDING", NOW.minusDays(1), null);
    when(goalProposalDao.selectProposalById(9L)).thenReturn(Optional.of(p));
    when(goalProposalDao.markResponded(9L, "ACCEPTED", NOW)).thenReturn(0);

    assertThatThrownBy(() -> service.respond(1L, 9L, true, TODAY, NOW))
        .isInstanceOf(ResponseStatusException.class);
    verify(frequencyGoalService, never()).applyAgreedGoal(anyLong(), anyInt(), any());
  }

  // ===================== 会員が自分で変える =====================

  @Test
  void 会員が週の回数目標を変えたら会員が変更を記録し同じ値なら記録しない() {
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(3));

    service.updateWeeklyTargetByMember(1L, 2, TODAY, NOW);
    service.updateWeeklyTargetByMember(1L, 3, TODAY, NOW);

    verify(frequencyGoalService).updateGoal(1L, 2, TODAY);
    verify(frequencyGoalService).updateGoal(1L, 3, TODAY);
    ArgumentCaptor<MemberGoalChange> captor = ArgumentCaptor.forClass(MemberGoalChange.class);
    verify(goalProposalDao).insertChange(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo("WEEKLY_TARGET");
    assertThat(captor.getValue().getOldValue()).isEqualTo("3");
    assertThat(captor.getValue().getNewValue()).isEqualTo("2");
    assertThat(captor.getValue().getChangedAt()).isEqualTo(NOW);
  }

  @Test
  void 会員が目的モードを変えたら会員が変更を記録し同じ値なら記録しない() {
    service.updateGoalModeByMember(1, "BULKING", NOW);
    service.updateGoalModeByMember(1, "MAINTENANCE", NOW);

    verify(profileService).updateGoalMode(1, "BULKING");
    ArgumentCaptor<MemberGoalChange> captor = ArgumentCaptor.forClass(MemberGoalChange.class);
    verify(goalProposalDao).insertChange(captor.capture());
    assertThat(captor.getValue().getChangeType()).isEqualTo("GOAL_MODE");
    assertThat(captor.getValue().getOldValue()).isEqualTo("MAINTENANCE");
    assertThat(captor.getValue().getNewValue()).isEqualTo("BULKING");
  }

  // ===================== 担当トレーナーと決めた目標の表示 =====================

  @Test
  void 承認した提案のままなら担当トレーナーと決めた目標と次の見直し日を出す() {
    member.setCurrentGoalMode("BULKING");
    proposals.add(proposal(9L, 3, "BULKING", "ACCEPTED", NOW.minusDays(1), NOW));
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(3));

    AgreementView v = service.agreementFor(1L).orElseThrow();

    assertThat(v.tagLabel()).isEqualTo("担当トレーナーと決めた目標（11/24 山本 健）");
    assertThat(v.nextReviewLabel()).isEqualTo("12/22");
  }

  @Test
  void 承認のあとに会員が自分で変えたら表示を外す() {
    member.setCurrentGoalMode("BULKING");
    proposals.add(proposal(9L, 3, "BULKING", "ACCEPTED", NOW.minusDays(1), NOW));
    changes.add(change("GOAL_MODE", "MAINTENANCE", NOW.plusHours(1)));
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(3));

    assertThat(service.agreementFor(1L)).isEmpty();
  }

  @Test
  void 承認より前の会員の変更や新しい提案の見送りでは表示を外さない() {
    member.setCurrentGoalMode("BULKING");
    proposals.add(proposal(10L, 5, "CUTTING", "DECLINED", NOW.plusDays(2), NOW.plusDays(3)));
    proposals.add(proposal(9L, 3, "BULKING", "ACCEPTED", NOW.minusDays(1), NOW));
    changes.add(change("WEEKLY_TARGET", "2", NOW.minusDays(5)));
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(3));

    assertThat(service.agreementFor(1L)).isPresent();
  }

  @Test
  void 承認した提案が無ければ表示しない() {
    proposals.add(proposal(9L, 3, "BULKING", "PENDING", NOW, null));

    assertThat(service.agreementFor(1L)).isEmpty();
  }

  // ===================== 会員のトップの提案 =====================

  @Test
  void 会員のトップには返事待ちの提案を今の目標と並べて出す() {
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(2));
    GoalProposal p = proposal(9L, 3, "BULKING", "PENDING", NOW, null);
    p.setComment("まずは週3回を4週続けましょう");
    proposals.add(p);

    MemberProposalView v = service.pendingForMember(1L).orElseThrow();

    assertThat(v.title()).isEqualTo("山本 健トレーナーから目標の提案があります");
    assertThat(v.currentWeeklyLabel()).isEqualTo("週2回");
    assertThat(v.proposedWeeklyLabel()).isEqualTo("週3回");
    assertThat(v.currentGoalModeLabel()).isEqualTo("維持");
    assertThat(v.proposedGoalModeLabel()).isEqualTo("筋肥大");
    assertThat(v.weeklyChanged()).isTrue();
    assertThat(v.nextReviewLabel()).isEqualTo("12/22");
  }

  @Test
  void 返事待ちの提案が無ければ会員のトップには出さない() {
    proposals.add(proposal(9L, 3, "BULKING", "DECLINED", NOW.minusDays(1), NOW));

    assertThat(service.pendingForMember(1L)).isEmpty();
  }

  // ===================== スタッフ向けの会員詳細 =====================

  @Test
  void 会員詳細の履歴は新しい順で承認見送り取り下げ会員が変更と返事待ちを出す() {
    when(frequencyGoalService.findManualTarget(1L)).thenReturn(Optional.of(2));
    when(frequencyGoalService.getSummary(eq(1L), any()))
        .thenReturn(new FrequencyGoalSummary(true, 2, 0, false, 0, 0, 2, true));
    GoalProposal accepted = proposal(3L, 3, "BULKING", "ACCEPTED", at(11, 24, 9), at(11, 24, 12));
    accepted.setComment("まずは週3回を4週続けましょう");
    accepted.setNextReviewDate(LocalDate.of(2026, 12, 22));
    GoalProposal declined = proposal(2L, 4, "BULKING", "DECLINED", at(11, 20, 9), at(11, 21, 9));
    declined.setNextReviewDate(LocalDate.of(2026, 12, 18));
    GoalProposal withdrawn = proposal(1L, 5, "CUTTING", "WITHDRAWN", at(11, 18, 9), at(11, 20, 9));
    GoalProposal pending = proposal(4L, 3, "MAINTENANCE", "PENDING", at(12, 1, 9), null);
    proposals.addAll(List.of(pending, accepted, declined, withdrawn));
    changes.add(change("WEEKLY_TARGET", "2", at(11, 30, 9)));

    StaffGoalView v = service.staffView(TRAINER, member, TODAY);

    assertThat(v.history())
        .extracting(HistoryRow::dateLabel, HistoryRow::statusLabel)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("12/1", "返事待ち"),
            org.assertj.core.groups.Tuple.tuple("11/30", "会員が変更"),
            org.assertj.core.groups.Tuple.tuple("11/24", "承認（11/24）"),
            org.assertj.core.groups.Tuple.tuple("11/20", "見送り（11/21）"),
            org.assertj.core.groups.Tuple.tuple("11/18", "取り下げ（11/20）"));
    assertThat(v.history().get(1).content()).isEqualTo("週の回数目標を週2回に変更");
    assertThat(v.history().get(2).content())
        .isEqualTo("週3回・筋肥大・「まずは週3回を4週続けましょう」・見直し日12/22（山本 健が提案）");
    assertThat(v.history().get(3).content()).isEqualTo("週4回・筋肥大・見直し日12/18（山本 健が提案）");
    assertThat(v.pendingLabel()).isEqualTo("返事待ちの提案があります（12/1 山本 健）");
    assertThat(v.currentLabel()).isEqualTo("週2回・維持（会員が設定）");
    assertThat(v.programActive()).isTrue();
    assertThat(v.defaultReviewDate()).isEqualTo(TODAY.plusDays(28));
    assertThat(v.minReviewDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(v.defaultWeeklyTarget()).isEqualTo(2);
  }

  // ===================== 見直し日の印 =====================

  @Test
  void 見直し日の印は最新の提案の見直し日が今日以前のとき() {
    GoalProposal due = proposal(1L, 3, "BULKING", "DECLINED", NOW, NOW);
    due.setNextReviewDate(TODAY);
    GoalProposal notYet = proposal(2L, 3, "BULKING", "PENDING", NOW, null);
    notYet.setMemberUserId(2L);
    notYet.setNextReviewDate(TODAY.plusDays(1));
    when(goalProposalDao.selectLatestProposalsByMemberIds(List.of(1L, 2L, 3L)))
        .thenReturn(List.of(due, notYet));

    Map<Long, ReviewStatus> m = service.reviewStatuses(List.of(1L, 2L, 3L), TODAY);

    assertThat(m.get(1L).due()).isTrue();
    assertThat(m.get(2L).due()).isFalse();
    assertThat(m).doesNotContainKey(3L);
  }

  // ===================== helpers =====================

  private static LocalDateTime at(int month, int day, int hour) {
    return LocalDateTime.of(2026, month, day, hour, 0);
  }

  private static GoalProposal proposal(
      Long id,
      int count,
      String mode,
      String status,
      LocalDateTime proposedAt,
      LocalDateTime respondedAt) {
    GoalProposal p = new GoalProposal();
    p.setId(id);
    p.setMemberUserId(1L);
    p.setProposerUserId(100L);
    p.setProposerName("山本 健");
    p.setWeeklyTargetCount(count);
    p.setGoalMode(mode);
    p.setNextReviewDate(LocalDate.of(2026, 12, 22));
    p.setStatus(status);
    p.setProposedAt(proposedAt);
    p.setRespondedAt(respondedAt);
    return p;
  }

  private static MemberGoalChange change(String type, String newValue, LocalDateTime at) {
    MemberGoalChange c = new MemberGoalChange();
    c.setId(1L);
    c.setMemberUserId(1L);
    c.setChangeType(type);
    c.setNewValue(newValue);
    c.setChangedAt(at);
    return c;
  }

  private static User staff(int id, String role, Long organizationId, String name) {
    return User.builder()
        .userId(id)
        .userName(name)
        .role(role)
        .organizationId(organizationId)
        .build();
  }
}
