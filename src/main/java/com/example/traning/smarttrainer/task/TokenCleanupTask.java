package com.example.traning.smarttrainer.task;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenCleanupTask {

    private final PasswordResetTokenDao tokenDao;

    public TokenCleanupTask(PasswordResetTokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    // 1時間ごとに実行
    @Scheduled(cron = "${batch.master.tokenCleanup.cron}")
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        log.info("トークンクリーンアップを開始します...");
        tokenDao.deleteExpiredTokens(now); // Daoに一括削除用のメソッドを作る
    }
}
