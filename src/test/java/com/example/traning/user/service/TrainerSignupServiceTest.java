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
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.form.TrainerSignupForm;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * ita4-3: {@link TrainerSignupService}のパスワード一致検証・招待コード連携・
 * ROLE_STORE_ADMINとしての登録を検証する。DAO・招待コードサービスはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class TrainerSignupServiceTest {

  @Mock private UserDao userDao;
  @Mock private InviteCodeService inviteCodeService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private AccountRestoreService accountRestoreService;

  private TrainerSignupService service;

  @BeforeEach
  void setUp() {
    service =
        new TrainerSignupService(
            userDao, inviteCodeService, passwordEncoder, accountRestoreService);
  }

  private TrainerSignupForm validForm() {
    TrainerSignupForm form = new TrainerSignupForm();
    form.setUsername("trainer1");
    form.setEmail("trainer1@example.com");
    form.setPassword("Password1!");
    form.setPassword_confirm("Password1!");
    form.setInviteCode("ABCDEFGHIJ");
    return form;
  }

  @Test
  void register_招待コードの組織でROLE_STORE_ADMINとして登録される() {
    TrainerSignupForm form = validForm();
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());
    InviteCode code = new InviteCode();
    code.setOrganizationId(7L);
    when(inviteCodeService.redeem("ABCDEFGHIJ")).thenReturn(code);
    when(passwordEncoder.encode("Password1!")).thenReturn("encoded-password");

    service.register(form);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userDao).insert(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getRole()).isEqualTo(Role.STORE_ADMIN.value());
    assertThat(saved.getOrganizationId()).isEqualTo(7L);
    assertThat(saved.getPassword()).isEqualTo("encoded-password");
    assertThat(saved.getEmail()).isEqualTo("trainer1@example.com");
  }

  @Test
  void register_パスワード不一致は例外で招待コードを消費しない() {
    TrainerSignupForm form = validForm();
    form.setPassword_confirm("different");

    assertThatThrownBy(() -> service.register(form)).isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeService, never()).redeem(any());
    verify(userDao, never()).insert(any(User.class));
  }

  @Test
  void register_招待コードが無効なら例外でユーザーは作成されない() {
    TrainerSignupForm form = validForm();
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.empty());
    when(inviteCodeService.redeem("ABCDEFGHIJ"))
        .thenThrow(new IllegalArgumentException("この招待コードは失効しています"));

    assertThatThrownBy(() -> service.register(form)).isInstanceOf(IllegalArgumentException.class);
    verify(userDao, never()).insert(any(User.class));
  }

  @Test
  void register_論理削除済みメールは復元フローに委ねてユーザーを作成しない() {
    TrainerSignupForm form = validForm();
    when(userDao.selectSoftDeletedByEmail(form.getEmail())).thenReturn(Optional.of(new User()));

    assertThatThrownBy(() -> service.register(form))
        .isInstanceOf(AccountRestoreRequiredException.class);
    verify(accountRestoreService).initiateRestore(form.getEmail());
    verify(inviteCodeService, never()).redeem(any());
    verify(userDao, never()).insert(any(User.class));
  }
}
