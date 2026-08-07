package com.example.traning.withdrawal;

import com.example.traning.common.MailService;
import com.example.traning.dao.UserDao;
import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;
import com.example.traning.goal.GoalDao;
import com.example.traning.mfa.MfaBackupCodeDao;
import com.example.traning.mfa.MfaSettingDao;
import com.example.traning.mobile.dao.MobileDeviceTokenDao;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.pr.dao.PersonalRecordDao;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import com.example.traning.user.dao.AccountRestoreTokenDao;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
      MailService mailService) {
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
  }

  @Transactional(readOnly = true)
  public boolean hasPendingRequest(Long userId) {
    return withdrawalRequestDao.selectPendingByUserId(userId).isPresent();
  }

  @Transactional(readOnly = true)
  public List<WithdrawalRequestWithUser> findAllPendingWithUser() {
    List<WithdrawalRequest> requests = withdrawalRequestDao.selectAllPending();
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
  public void approveRequest(Long requestId, Long adminUserId) {
    WithdrawalRequest req =
        withdrawalRequestDao
            .selectById(requestId)
            .orElseThrow(() -> new IllegalStateException("申請が見つかりません"));

    if (!"PENDING".equals(req.getStatus())) {
      throw new IllegalStateException("この申請は処理済みです");
    }

    User targetUser = userDao.selectById(req.getUserId().intValue());

    // ① 退会完了メールを先に送信（削除後はアドレスが消えるため）
    // 通知メール送信失敗（SMTP障害等）でデータ保護期間対応（物理削除）自体を止めない。
    LocalDateTime completedAt = LocalDateTime.now();
    try {
      mailService.sendWithdrawalCompletedMail(
          targetUser.getEmail(), targetUser.getUserName(), completedAt);
    } catch (Exception e) {
      log.warn("退会完了の通知メール送信に失敗しました - userId: {}", req.getUserId(), e);
    }

    // ② training_details を物理削除（FK制約のため trainings より先に削除）
    trainingDetailDao.deleteByUserId(req.getUserId());

    // ③ trainings を物理削除
    trainingDao.deleteByUserId(req.getUserId());

    // ④ personal_records を物理削除
    personalRecordDao.deleteByUserId(req.getUserId());

    // ⑤ training_goals を物理削除
    goalDao.deleteByUserId(req.getUserId());

    // ⑥ MFA データを物理削除
    mfaBackupCodeDao.deleteByUserId(req.getUserId());
    mfaSettingDao.deleteByUserId(req.getUserId());

    // ⑦ パスワードリセット・アカウント復元トークンを物理削除
    passwordResetTokenDao.deleteByUserId(req.getUserId().intValue());
    accountRestoreTokenDao.deleteByUserId(req.getUserId().intValue());

    // ⑧ モバイルのリフレッシュトークン・デバイストークン（プッシュ通知用）を物理削除
    mobileRefreshTokenDao.deleteByUserId(req.getUserId());
    mobileDeviceTokenDao.deleteByUserId(req.getUserId());

    // ⑨ 申請ステータスを APPROVED に更新
    req.setStatus("APPROVED");
    req.setProcessedAt(completedAt);
    req.setProcessedBy(adminUserId);
    req.setUpdatedAt(LocalDateTime.now());
    withdrawalRequestDao.update(req);

    // ⑩ ユーザーを論理削除（deleted_at 設定）
    userDao.softDeleteById(targetUser.getUserId());

    log.info("Withdrawal approved - userId: {}, adminId: {}", req.getUserId(), adminUserId);
  }

  @Transactional
  public void rejectRequest(Long requestId, Long adminUserId) {
    WithdrawalRequest req =
        withdrawalRequestDao
            .selectById(requestId)
            .orElseThrow(() -> new IllegalStateException("申請が見つかりません"));

    if (!"PENDING".equals(req.getStatus())) {
      throw new IllegalStateException("この申請は処理済みです");
    }

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
