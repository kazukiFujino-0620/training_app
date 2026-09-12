package com.example.traning.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.common.MailService;
import com.example.traning.dao.UserDao;
import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;
import com.example.traning.goal.GoalDao;
import com.example.traning.mfa.MfaBackupCodeDao;
import com.example.traning.mfa.MfaSettingDao;
import com.example.traning.mobile.dao.MobileDeviceTokenDao;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.pr.dao.PersonalRecordDao;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import com.example.traning.user.dao.AccountRestoreTokenDao;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 一般ユーザー（招待コードなし登録）の即時退会と、ジム所属ユーザーの申請制退会の分岐を検証する。 */
@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

  @Mock private WithdrawalRequestDao withdrawalRequestDao;
  @Mock private UserDao userDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private TrainingDao trainingDao;
  @Mock private PersonalRecordDao personalRecordDao;
  @Mock private GoalDao goalDao;
  @Mock private MfaSettingDao mfaSettingDao;
  @Mock private MfaBackupCodeDao mfaBackupCodeDao;
  @Mock private PasswordResetTokenDao passwordResetTokenDao;
  @Mock private AccountRestoreTokenDao accountRestoreTokenDao;
  @Mock private MobileRefreshTokenDao mobileRefreshTokenDao;
  @Mock private MobileDeviceTokenDao mobileDeviceTokenDao;
  @Mock private MailService mailService;
  @Mock private OrganizationScopeResolver organizationScopeResolver;

  private WithdrawalService service;

  @BeforeEach
  void setUp() {
    service =
        new WithdrawalService(
            withdrawalRequestDao,
            userDao,
            trainingDetailDao,
            trainingDao,
            personalRecordDao,
            goalDao,
            mfaSettingDao,
            mfaBackupCodeDao,
            passwordResetTokenDao,
            accountRestoreTokenDao,
            mobileRefreshTokenDao,
            mobileDeviceTokenDao,
            mailService,
            organizationScopeResolver);
  }

  private User user(int id, long organizationId) {
    User u = new User();
    u.setUserId(id);
    u.setEmail("taro@example.com");
    u.setUserName("テスト太郎");
    u.setOrganizationId(organizationId);
    return u;
  }

  @Test
  void isGeneralUser_デフォルト組織所属ならtrue() {
    User general = user(1, Organization.DEFAULT_STORE_ORGANIZATION_ID);
    assertThat(service.isGeneralUser(general)).isTrue();
  }

  @Test
  void isGeneralUser_実在のジム組織所属ならfalse() {
    User member = user(1, 999L);
    assertThat(service.isGeneralUser(member)).isFalse();
  }

  @Test
  void selfDeleteImmediately_一般ユーザーは即座にデータが物理削除されユーザーが論理削除される() {
    User general = user(1, Organization.DEFAULT_STORE_ORGANIZATION_ID);
    when(userDao.selectById(1)).thenReturn(general);
    when(withdrawalRequestDao.selectPendingByUserId(1L)).thenReturn(Optional.empty());

    service.selfDeleteImmediately(1L);

    verify(trainingDetailDao).deleteByUserId(1L);
    verify(trainingDao).deleteByUserId(1L);
    verify(personalRecordDao).deleteByUserId(1L);
    verify(goalDao).deleteByUserId(1L);
    verify(mfaBackupCodeDao).deleteByUserId(1L);
    verify(mfaSettingDao).deleteByUserId(1L);
    verify(passwordResetTokenDao).deleteByUserId(1);
    verify(accountRestoreTokenDao).deleteByUserId(1);
    verify(mobileRefreshTokenDao).deleteByUserId(1L);
    verify(mobileDeviceTokenDao).deleteByUserId(1L);
    verify(userDao).softDeleteById(1);
    // 一般ユーザーなので管理者承認は発生せず、記録用にAPPROVED状態のリクエストが直接insertされる
    verify(withdrawalRequestDao).insert(any(WithdrawalRequest.class));
  }

  @Test
  void selfDeleteImmediately_ジム所属ユーザーは拒否される() {
    User member = user(1, 999L);
    when(userDao.selectById(1)).thenReturn(member);

    assertThatThrownBy(() -> service.selfDeleteImmediately(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("一般ユーザー");

    verify(trainingDao, never()).deleteByUserId(anyLong());
    verify(userDao, never()).softDeleteById(anyInt());
  }

  @Test
  void selfDeleteImmediately_既に申請中なら拒否される() {
    User general = user(1, Organization.DEFAULT_STORE_ORGANIZATION_ID);
    when(userDao.selectById(1)).thenReturn(general);
    when(withdrawalRequestDao.selectPendingByUserId(1L))
        .thenReturn(Optional.of(new WithdrawalRequest()));

    assertThatThrownBy(() -> service.selfDeleteImmediately(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("既に退会申請中です");

    verify(trainingDao, never()).deleteByUserId(anyLong());
  }

  @Test
  void createRequest_一般ユーザーは拒否される() {
    User general = user(1, Organization.DEFAULT_STORE_ORGANIZATION_ID);
    when(userDao.selectById(1)).thenReturn(general);

    assertThatThrownBy(() -> service.createRequest(1L, "OTHER", "テスト"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ジム所属ユーザー");

    verify(withdrawalRequestDao, never()).insert(any(WithdrawalRequest.class));
  }

  @Test
  void cancelRequest_一般ユーザーは拒否される() {
    User general = user(1, Organization.DEFAULT_STORE_ORGANIZATION_ID);
    when(userDao.selectById(1)).thenReturn(general);

    assertThatThrownBy(() -> service.cancelRequest(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ジム所属ユーザー");

    verify(withdrawalRequestDao, never()).update(any(WithdrawalRequest.class));
  }
}
