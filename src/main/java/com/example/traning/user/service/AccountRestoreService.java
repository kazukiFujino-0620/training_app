package com.example.traning.user.service;

import com.example.traning.common.MailService;
import com.example.traning.dao.UserDao;
import com.example.traning.user.AccountRestoreToken;
import com.example.traning.user.User;
import com.example.traning.user.dao.AccountRestoreTokenDao;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountRestoreService {

  private final UserDao userDao;
  private final AccountRestoreTokenDao restoreTokenDao;
  private final MailService mailService;

  public AccountRestoreService(
      UserDao userDao, AccountRestoreTokenDao restoreTokenDao, MailService mailService) {
    this.userDao = userDao;
    this.restoreTokenDao = restoreTokenDao;
    this.mailService = mailService;
  }

  /** 論理削除済みアカウントの復元フローを開始する。 呼び出し元のトランザクションとは独立してコミットする（REQUIRES_NEW）。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void initiateRestore(String email) {
    User user =
        userDao
            .selectSoftDeletedByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("復元対象のユーザーが見つかりません"));

    // 既存のトークンを削除してから新規発行する
    restoreTokenDao.deleteByUserId(user.getUserId());

    String rawToken = UUID.randomUUID().toString();
    AccountRestoreToken restoreToken = new AccountRestoreToken();
    restoreToken.setUserId(user.getUserId());
    restoreToken.setToken(rawToken);
    restoreToken.setExpiryDate(LocalDateTime.now().plusHours(24));
    restoreTokenDao.insert(restoreToken);

    mailService.sendRestoreMail(email, rawToken);
    log.info("アカウント復元メール送信完了 - userIdHash: {}", user.getUserId().hashCode());
  }

  /** トークンを検証してアカウントを復元する（deleted_at = NULL）。 */
  @Transactional
  public void restoreAccount(String token) {
    AccountRestoreToken restoreToken =
        restoreTokenDao
            .selectByToken(token)
            .orElseThrow(() -> new IllegalArgumentException("無効または期限切れのURLです。"));

    if (restoreToken.expiryDate.isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("URLの有効期限が切れています。");
    }

    userDao.restoreById(restoreToken.userId);
    restoreTokenDao.deleteByUserId(restoreToken.userId);

    log.info("アカウント復元完了 - userIdHash: {}", restoreToken.userId.hashCode());
  }
}
