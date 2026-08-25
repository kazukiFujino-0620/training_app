package com.example.traning.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * ita4-4 (A): {@link TrainerAdviceService}の宛先スコープ検証・削除権限を検証する。
 * DAO・UserService・OrganizationScopeResolverはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class TrainerAdviceServiceTest {

  @Mock private TrainerAdviceDao trainerAdviceDao;
  @Mock private UserService userService;
  @Mock private OrganizationScopeResolver organizationScopeResolver;
  @Mock private ProfileService profileService;

  private TrainerAdviceService service;

  @BeforeEach
  void setUp() {
    service =
        new TrainerAdviceService(
            trainerAdviceDao, userService, organizationScopeResolver, profileService);
  }

  private User user(int id, String role, Long organizationId) {
    return user(id, role, organizationId, null);
  }

  private User user(int id, String role, Long organizationId, Long assignedTrainerId) {
    return User.builder()
        .userId(id)
        .role(role)
        .organizationId(organizationId)
        .assignedTrainerId(assignedTrainerId)
        .build();
  }

  @Test
  void listTrainees_自スコープ内のROLE_USERのみ返す() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    User traineeInScope = user(2, "ROLE_USER", 10L);
    User traineeOutOfScope = user(3, "ROLE_USER", 999L);
    User otherTrainer = user(4, "ROLE_STORE_ADMIN", 10L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainer))
        .thenReturn(Set.of(10L));
    when(userService.findAll())
        .thenReturn(List.of(traineeInScope, traineeOutOfScope, otherTrainer));

    List<User> result = service.listTrainees(trainer);

    assertThat(result).containsExactly(traineeInScope);
  }

  @Test
  void listTrainees_他のトレーナーが担当のトレーニーは除外する() {
    User trainerA = user(1, "ROLE_STORE_ADMIN", 10L);
    User assignedToA = user(2, "ROLE_USER", 10L, 1L);
    User assignedToB = user(3, "ROLE_USER", 10L, 99L);
    User unassigned = user(4, "ROLE_USER", 10L, null);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainerA))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(assignedToA, assignedToB, unassigned));

    List<User> result = service.listTrainees(trainerA);

    assertThat(result).containsExactlyInAnyOrder(assignedToA, unassigned);
  }

  @Test
  void send_未割り当てのトレーニーへの初回送信で自動的に担当トレーナーとして割り当てられる() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    User unassignedTrainee = user(2, "ROLE_USER", 10L, null);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainer))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(unassignedTrainee));

    service.send(trainer, 2L, "頑張りましょう", LocalDate.of(2026, 8, 23));

    verify(profileService).updateAssignedTrainer(2, 1L);
  }

  @Test
  void send_既に別のトレーナーが担当のトレーニーへは送信できない() {
    User trainerB = user(2, "ROLE_STORE_ADMIN", 10L);
    User assignedToA = user(3, "ROLE_USER", 10L, 1L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainerB))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(assignedToA));

    assertThatThrownBy(() -> service.send(trainerB, 3L, "私からもアドバイス", LocalDate.of(2026, 8, 23)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(profileService, never()).updateAssignedTrainer(any(), any());
    verify(trainerAdviceDao, never()).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_自分が担当のトレーニーへは再割り当てせず送信できる() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    User assignedToSelf = user(2, "ROLE_USER", 10L, 1L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainer))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(assignedToSelf));

    service.send(trainer, 2L, "続けましょう", LocalDate.of(2026, 8, 23));

    verify(profileService, never()).updateAssignedTrainer(any(), any());
    verify(trainerAdviceDao).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_スコープ内のトレーニーへは送信できる() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    User trainee = user(2, "ROLE_USER", 10L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainer))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(trainee));

    TrainerAdvice result = service.send(trainer, 2L, "頑張りましょう", LocalDate.of(2026, 8, 23));

    assertThat(result.getTrainerId()).isEqualTo(1L);
    assertThat(result.getTargetUserId()).isEqualTo(2L);
    assertThat(result.getBody()).isEqualTo("頑張りましょう");
    assertThat(result.getTargetDate()).isEqualTo(LocalDate.of(2026, 8, 23));
    verify(trainerAdviceDao).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_スコープ外のユーザーへは送信できない() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    User outOfScopeUser = user(3, "ROLE_USER", 999L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(trainer))
        .thenReturn(Set.of(10L));
    when(userService.findAll()).thenReturn(List.of(outOfScopeUser));

    assertThatThrownBy(() -> service.send(trainer, 3L, "不正送信", LocalDate.of(2026, 8, 23)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(trainerAdviceDao, never()).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_本文が空の場合は例外() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);

    assertThatThrownBy(() -> service.send(trainer, 2L, "  ", LocalDate.of(2026, 8, 23)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(trainerAdviceDao, never()).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_本文が1000文字を超える場合は例外() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    String tooLong = "あ".repeat(1001);

    assertThatThrownBy(() -> service.send(trainer, 2L, tooLong, LocalDate.of(2026, 8, 23)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(trainerAdviceDao, never()).insert(any(TrainerAdvice.class));
  }

  @Test
  void send_対象日が未指定の場合は例外() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);

    assertThatThrownBy(() -> service.send(trainer, 2L, "頑張りましょう", null))
        .isInstanceOf(IllegalArgumentException.class);
    verify(trainerAdviceDao, never()).insert(any(TrainerAdvice.class));
  }

  @Test
  void getActiveForUser_DAOの結果をそのまま返す() {
    TrainerAdvice advice = new TrainerAdvice();
    when(trainerAdviceDao.selectActiveByTargetUserId(2L)).thenReturn(List.of(advice));

    List<TrainerAdvice> result = service.getActiveForUser(2L);

    assertThat(result).containsExactly(advice);
  }

  @Test
  void getActiveForUserAndDate_DAOの結果をそのまま返す() {
    TrainerAdvice advice = new TrainerAdvice();
    LocalDate date = LocalDate.of(2026, 8, 23);
    when(trainerAdviceDao.selectActiveByTargetUserIdAndDate(2L, date)).thenReturn(List.of(advice));

    List<TrainerAdvice> result = service.getActiveForUserAndDate(2L, date);

    assertThat(result).containsExactly(advice);
  }

  @Test
  void markAsRead_未読のみ既読にしてDAO更新する() {
    TrainerAdvice unread = new TrainerAdvice();
    unread.setId(1L);
    TrainerAdvice alreadyRead = new TrainerAdvice();
    alreadyRead.setId(2L);
    alreadyRead.setReadAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0));

    service.markAsRead(List.of(unread, alreadyRead));

    assertThat(unread.getReadAt()).isNotNull();
    verify(trainerAdviceDao).update(unread);
    verify(trainerAdviceDao, never()).update(alreadyRead);
  }

  @Test
  void delete_送信者本人は取り下げできる() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    TrainerAdvice advice = new TrainerAdvice();
    advice.setId(100L);
    advice.setTrainerId(1L);
    when(trainerAdviceDao.selectById(100L)).thenReturn(Optional.of(advice));

    service.delete(trainer, 100L);

    assertThat(advice.getDeletedAt()).isNotNull();
    verify(trainerAdviceDao).update(advice);
  }

  @Test
  void delete_送信者以外は取り下げできない() {
    User otherTrainer = user(2, "ROLE_STORE_ADMIN", 10L);
    TrainerAdvice advice = new TrainerAdvice();
    advice.setId(100L);
    advice.setTrainerId(1L);
    when(trainerAdviceDao.selectById(100L)).thenReturn(Optional.of(advice));

    assertThatThrownBy(() -> service.delete(otherTrainer, 100L))
        .isInstanceOf(ResponseStatusException.class);
    verify(trainerAdviceDao, never()).update(any(TrainerAdvice.class));
  }

  @Test
  void delete_存在しないアドバイスは例外() {
    User trainer = user(1, "ROLE_STORE_ADMIN", 10L);
    when(trainerAdviceDao.selectById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(trainer, 999L))
        .isInstanceOf(ResponseStatusException.class);
  }
}
