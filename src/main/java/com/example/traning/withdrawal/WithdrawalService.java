package com.example.traning.withdrawal;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class WithdrawalService {

  private final WithdrawalRequestDao withdrawalRequestDao;
  private final UserDao userDao;
  private final TrainingDetailDao trainingDetailDao;
  private final TrainingDao trainingDao;
  private final PersonalRecordDao personalRecordDao;
  private final GoalDao goalDao;
  private final MfaSettingDao mfaSettingDao;
  private final MfaBackupCodeDao mfaBackupCodeDao;
  private final PasswordResetTokenDao passwordResetTokenDao;
  private final AccountRestoreTokenDao accountRestoreTokenDao;
  private final MobileRefreshTokenDao mobileRefreshTokenDao;
  private final MobileDeviceTokenDao mobileDeviceTokenDao;
  private final MailService mailService;
  private final OrganizationScopeResolver organizationScopeResolver;

  public WithdrawalService(
      WithdrawalRequestDao withdrawalRequestDao,
      UserDao userDao,
      TrainingDetailDao trainingDetailDao,
      TrainingDao trainingDao,
      PersonalRecordDao personalRecordDao,
      GoalDao goalDao,
      MfaSettingDao mfaSettingDao,
      MfaBackupCodeDao mfaBackupCodeDao,
      PasswordResetTokenDao passwordResetTokenDao,
      AccountRestoreTokenDao accountRestoreTokenDao,
      MobileRefreshTokenDao mobileRefreshTokenDao,
      MobileDeviceTokenDao mobileDeviceTokenDao,
      MailService mailService,
      OrganizationScopeResolver organizationScopeResolver) {
    this.withdrawalRequestDao = withdrawalRequestDao;
    this.userDao = userDao;
    this.trainingDetailDao = trainingDetailDao;
    this.trainingDao = trainingDao;
    this.personalRecordDao = personalRecordDao;
    this.goalDao = goalDao;
    this.mfaSettingDao = mfaSettingDao;
    this.mfaBackupCodeDao = mfaBackupCodeDao;
    this.passwordResetTokenDao = passwordResetTokenDao;
    this.accountRestoreTokenDao = accountRestoreTokenDao;
    this.mobileRefreshTokenDao = mobileRefreshTokenDao;
    this.mobileDeviceTokenDao = mobileDeviceTokenDao;
    this.mailService = mailService;
    this.organizationScopeResolver = organizationScopeResolver;
  }

  @Transactional(readOnly = true)
  public boolean hasPendingRequest(Long userId) {
    return withdrawalRequestDao.selectPendingByUserId(userId).isPresent();
  }

  /**
   * 申請中の退会申請一覧を取得する。ADMINは全組織、ORG_ADMIN/STORE_ADMINは {@link OrganizationScopeResolver}
   * が解決する自スコープ（自組織・自店舗＋兼任店舗）分のみ返す。
   */
  @Transactional(readOnly = true)
  public List<WithdrawalRequestWithUser> findAllPendingWithUser(User currentAdmin) {
    Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(currentAdmin);
    List<WithdrawalRequest> requests =
        accessibleOrganizationIds == null
            ? withdrawalRequestDao.selectAllPending()
            : withdrawalRequestDao.selectPendingByOrganizationIds(
                new ArrayList<>(accessibleOrganizationIds));
    return requests.stream()
        .map(
            req -> {
              User user = userDao.selectById(req.getUserId().intValue());
              return new WithdrawalRequestWithUser(req, user);
            })
        .collect(Collectors.toList());
  }

  @Transactional
  public void createRequest(Long userId, String reasonType, String reasonText) {
    withdrawalRequestDao
        .selectPendingByUserId(userId)
        .ifPresent(
            r -> {
              throw new IllegalStateException("既に退会申請中です");
            });

    User user = userDao.selectById(userId.intValue());

    WithdrawalRequest req = new WithdrawalRequest();
    req.setUserId(userId);
    req.setReasonType(reasonType);
    req.setReasonText(reasonText);
    req.setStatus("PENDING");
    req.setRequestedAt(LocalDateTime.now());
    req.setCreatedAt(LocalDateTime.now());
    req.setUpdatedAt(LocalDateTime.now());
    req.setOrganizationId(user.getOrganizationId());
    withdrawalRequestDao.insert(req);

    // 通知メール送信失敗（SMTP障害等）で申請自体を失敗させない。
    // 申請の成立はDB登録で完結しており、メールはあくまで通知。
    try {
      mailService.sendWithdrawalRequestedMail(
          user.getEmail(), user.getUserName(), req.getRequestedAt());
    } catch (Exception e) {
      log.warn("退会申請の通知メール送信に失敗しました - userId: {}", userId, e);
    }
    log.info("Withdrawal request created - userId: {}", userId);
  }

  @Transactional
  public void cancelRequest(Long userId) {
    WithdrawalRequest req =
        withdrawalRequestDao
            .selectPendingByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("申請中の退会申請がありません"));

    req.setStatus("CANCELLED");
    req.setProcessedAt(LocalDateTime.now());
    req.setUpdatedAt(LocalDateTime.now());
    withdrawalRequestDao.update(req);
    log.info("Withdrawal request cancelled - userId: {}", userId);
  }

  @Transactional
  public void approveRequest(Long requestId, User currentAdmin) {
    WithdrawalRequest req =
        withdrawalRequestDao
            .selectById(requestId)
            .orElseThrow(() -> new IllegalStateException("申請が見つかりません"));

    if (!"PENDING".equals(req.getStatus())) {
      throw new IllegalStateException("この申請は処理済みです");
    }
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, req.getOrganizationId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この申請を操作する権限がありません");
    }
    Long adminUserId = currentAdmin.getUserId().longValue();

    User targetUser = userDao.selectById(req.getUserId().intValue());
    LocalDateTime completedAt = deleteUserDataAndAccount(targetUser);

    req.setStatus("APPROVED");
    req.setProcessedAt(completedAt);
    req.setProcessedBy(adminUserId);
    req.setUpdatedAt(LocalDateTime.now());
    withdrawalRequestDao.update(req);

    log.info("Withdrawal approved - userId: {}, adminId: {}", req.getUserId(), adminUserId);
  }

  /**
   * 一般ユーザー（招待コードなし登録・デフォルト組織所属）向けの即時退会（ita3-3関連）。 ジム所属ユーザーと異なり管理者承認を挟まず、呼び出し時点で即座にデータを削除する。
   *
   * @throws IllegalStateException ジム所属ユーザー（一般ユーザーでない）、または既に退会申請中の場合
   */
  @Transactional
  public void selfDeleteImmediately(Long userId) {
    User targetUser = userDao.selectById(userId.intValue());
    if (targetUser == null) {
      throw new IllegalStateException("ユーザーが見つかりません");
    }
    if (!isGeneralUser(targetUser)) {
      throw new IllegalStateException("この操作は一般ユーザーのみ利用できます");
    }
    withdrawalRequestDao
        .selectPendingByUserId(userId)
        .ifPresent(
            r -> {
              throw new IllegalStateException("既に退会申請中です");
            });

    LocalDateTime completedAt = deleteUserDataAndAccount(targetUser);

    WithdrawalRequest req = new WithdrawalRequest();
    req.setUserId(userId);
    req.setStatus("APPROVED");
    req.setRequestedAt(completedAt);
    req.setProcessedAt(completedAt);
    req.setCreatedAt(completedAt);
    req.setUpdatedAt(completedAt);
    req.setOrganizationId(targetUser.getOrganizationId());
    withdrawalRequestDao.insert(req);

    log.info("Immediate self-withdrawal completed (general user) - userId: {}", userId);
  }

  /** ユーザーが一般ユーザー（招待コードなし登録・デフォルト組織所属）かどうかを判定する。 */
  @Transactional(readOnly = true)
  public boolean isGeneralUser(User user) {
    return user.getOrganizationId() != null
        && user.getOrganizationId() == Organization.DEFAULT_STORE_ORGANIZATION_ID;
  }

  /**
   * 退会完了メール送信＋関連データの物理削除＋ユーザーの論理削除を行う（{@link #approveRequest}・{@link
   * #selfDeleteImmediately}共通処理）。呼び出し元がトランザクション境界を持つこと。
   */
  private LocalDateTime deleteUserDataAndAccount(User targetUser) {
    Long userId = targetUser.getUserId().longValue();

    // ① 退会完了メールを先に送信（削除後はアドレスが消えるため）
    // 通知メール送信失敗（SMTP障害等）でデータ保護期間対応（物理削除）自体を止めない。
    LocalDateTime completedAt = LocalDateTime.now();
    try {
      mailService.sendWithdrawalCompletedMail(
          targetUser.getEmail(), targetUser.getUserName(), completedAt);
    } catch (Exception e) {
      log.warn("退会完了の通知メール送信に失敗しました - userId: {}", userId, e);
    }

    // ② training_details を物理削除（FK制約のため trainings より先に削除）
    trainingDetailDao.deleteByUserId(userId);

    // ③ trainings を物理削除
    trainingDao.deleteByUserId(userId);

    // ④ personal_records を物理削除
    personalRecordDao.deleteByUserId(userId);

    // ⑤ training_goals を物理削除
    goalDao.deleteByUserId(userId);

    // ⑥ MFA データを物理削除
    mfaBackupCodeDao.deleteByUserId(userId);
    mfaSettingDao.deleteByUserId(userId);

    // ⑦ パスワードリセット・アカウント復元トークンを物理削除
    passwordResetTokenDao.deleteByUserId(targetUser.getUserId());
    accountRestoreTokenDao.deleteByUserId(targetUser.getUserId());

    // ⑧ モバイルのリフレッシュトークン・デバイストークン（プッシュ通知用）を物理削除
    mobileRefreshTokenDao.deleteByUserId(userId);
    mobileDeviceTokenDao.deleteByUserId(userId);

    // ⑨ ユーザーを論理削除（deleted_at 設定）
    userDao.softDeleteById(targetUser.getUserId());

    return completedAt;
  }

  @Transactional
  public void rejectRequest(Long requestId, User currentAdmin) {
    WithdrawalRequest req =
        withdrawalRequestDao
            .selectById(requestId)
            .orElseThrow(() -> new IllegalStateException("申請が見つかりません"));

    if (!"PENDING".equals(req.getStatus())) {
      throw new IllegalStateException("この申請は処理済みです");
    }
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, req.getOrganizationId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この申請を操作する権限がありません");
    }
    Long adminUserId = currentAdmin.getUserId().longValue();

    req.setStatus("REJECTED");
    req.setProcessedAt(LocalDateTime.now());
    req.setProcessedBy(adminUserId);
    req.setUpdatedAt(LocalDateTime.now());
    withdrawalRequestDao.update(req);
    log.info("Withdrawal rejected - userId: {}, adminId: {}", req.getUserId(), adminUserId);
  }

  public record WithdrawalRequestWithUser(WithdrawalRequest request, User user) {}

  /** 管理者画面用：申請一覧のステータス表示マップ */
  public static Map<String, String> STATUS_LABELS =
      Map.of(
          "PENDING", "申請中",
          "APPROVED", "承認済み",
          "REJECTED", "拒否",
          "CANCELLED", "キャンセル");
}
