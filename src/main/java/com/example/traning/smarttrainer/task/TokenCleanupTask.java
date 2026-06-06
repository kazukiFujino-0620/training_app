package com.example.traning.smarttrainer.task;

import com.example.traning.emailchange.EmailChangeTokenDao;
import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;
import com.example.traning.user.dao.AccountRestoreTokenDao;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TokenCleanupTask {

  private final PasswordResetTokenDao tokenDao;
  private final AccountRestoreTokenDao restoreTokenDao;
  private final EmailChangeTokenDao emailChangeTokenDao;

  public TokenCleanupTask(
      PasswordResetTokenDao tokenDao,
      AccountRestoreTokenDao restoreTokenDao,
      EmailChangeTokenDao emailChangeTokenDao) {
    this.tokenDao = tokenDao;
    this.restoreTokenDao = restoreTokenDao;
    this.emailChangeTokenDao = emailChangeTokenDao;
  }

  @Scheduled(cron = "${batch.master.tokenCleanup.cron}")
  @org.springframework.transaction.annotation.Transactional
  public void cleanupExpiredTokens() {
    LocalDateTime now = LocalDateTime.now();
    log.info("トークンクリーンアップを開始します...");
    tokenDao.deleteExpiredTokens(now);
    restoreTokenDao.deleteExpiredTokens(now);
    emailChangeTokenDao.deleteExpiredTokens(now);
    log.info("トークンクリーンアップ完了");
  }
}
