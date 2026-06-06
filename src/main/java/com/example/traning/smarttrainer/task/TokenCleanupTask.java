package com.example.traning.smarttrainer.task;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.traning.emailchange.EmailChangeTokenDao;
import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;
import com.example.traning.user.dao.AccountRestoreTokenDao;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenCleanupTask {

    private final PasswordResetTokenDao tokenDao;
    private final AccountRestoreTokenDao restoreTokenDao;
    private final EmailChangeTokenDao emailChangeTokenDao;

    public TokenCleanupTask(PasswordResetTokenDao tokenDao, AccountRestoreTokenDao restoreTokenDao,
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
