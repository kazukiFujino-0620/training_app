package com.example.traning.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.organization.InviteCode;
import com.example.traning.organization.InviteCodeService;
import com.example.traning.organization.Organization;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** ita3-3: 一般ユーザー登録での招待コード解決（未入力=デフォルト組織、入力=招待コードの組織）を検証する。 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTransactionTest {

  @Mock private UserDao userDao;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AccountRestoreService accountRestoreService;
  @Mock private InviteCodeService inviteCodeService;

  private SignupServiceTransaction transaction;

  @BeforeEach
  void setUp() {
    transaction =
        new SignupServiceTransaction(
            userDao, passwordEncoder, accountRestoreService, inviteCodeService);
  }

  private SignupForm validForm() {
    SignupForm form = new SignupForm();
    form.setUsername("user1");
    form.setEmail("user1@example.com");
    form.setPassword("encoded-password");
    form.setPassword_confirm("encoded-password");
    return form;
  }

  @Test
  void execute_招待コード未入力ならデフォルト組織に割り当てられる() {
    SignupForm form = validForm();
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());

    boolean result = transaction.execute(form);

    assertThat(result).isTrue();
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userDao).insert(captor.capture());
    assertThat(captor.getValue().getOrganizationId())
        .isEqualTo(Organization.DEFAULT_STORE_ORGANIZATION_ID);
    verify(inviteCodeService, never()).redeem(any());
  }

  @Test
  void execute_有効な招待コード入力時はその組織に割り当てられる() {
    SignupForm form = validForm();
    form.setInviteCode("ABCDEFGHIJ");
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());
    InviteCode code = new InviteCode();
    code.setOrganizationId(5L);
    when(inviteCodeService.redeem("ABCDEFGHIJ")).thenReturn(code);

    transaction.execute(form);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userDao).insert(captor.capture());
    assertThat(captor.getValue().getOrganizationId()).isEqualTo(5L);
  }

  @Test
  void execute_無効な招待コードは例外でユーザーが作成されない() {
    SignupForm form = validForm();
    form.setInviteCode("INVALID001");
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());
    when(inviteCodeService.redeem("INVALID001"))
        .thenThrow(new IllegalArgumentException("招待コードが見つかりません"));

    assertThatThrownBy(() -> transaction.execute(form))
        .isInstanceOf(IllegalArgumentException.class);
    verify(userDao, never()).insert(any(User.class));
  }

  @Test
  void execute_招待コードの前後空白は無視される() {
    SignupForm form = validForm();
    form.setInviteCode("  ABCDEFGHIJ  ");
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());
    InviteCode code = new InviteCode();
    code.setOrganizationId(9L);
    when(inviteCodeService.redeem("ABCDEFGHIJ")).thenReturn(code);

    transaction.execute(form);

    verify(inviteCodeService).redeem("ABCDEFGHIJ");
  }

  @Test
  void execute_論理削除済みメールは復元フローに委ねて招待コードを消費しない() {
    SignupForm form = validForm();
    form.setInviteCode("ABCDEFGHIJ");
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.of(new User()));

    assertThatThrownBy(() -> transaction.execute(form))
        .isInstanceOf(AccountRestoreRequiredException.class);
    verify(inviteCodeService, never()).redeem(any());
    verify(userDao, never()).insert(any(User.class));
  }
}
