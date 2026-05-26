package com.example.traning.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * ブルートフォース攻撃対策のインメモリ・ロックアウト管理。
 *
 * - 同一キー（メール／IP）で連続失敗 MAX_ATTEMPTS 回 → LOCK_DURATION ロック
 * - ログイン成功で即時カウンタリセット
 * - 単一インスタンス前提（将来クラスタ化時は Redis などへ移行）
 *
 * 注: GCP Cloud Run / 単一 VM では十分機能する。複数インスタンスへ
 * スケールする場合は外部ストアへ切り替えること。
 */
@Service
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static class Attempt {
        int count;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    /**
     * 失敗を記録。閾値到達でロック開始。
     */
    public void loginFailed(String key) {
        if (key == null || key.isBlank()) return;
        attempts.compute(normalize(key), (k, v) -> {
            Attempt a = (v == null) ? new Attempt() : v;
            a.count++;
            if (a.count >= MAX_ATTEMPTS) {
                a.lockedUntil = Instant.now().plus(LOCK_DURATION);
                log.warn("Account locked due to {} failed attempts: keyHash={}",
                        MAX_ATTEMPTS, Integer.toHexString(k.hashCode()));
            }
            return a;
        });
    }

    /**
     * 成功時はカウンタクリア。
     */
    public void loginSucceeded(String key) {
        if (key == null || key.isBlank()) return;
        attempts.remove(normalize(key));
    }

    /**
     * 現在ロック中かどうか。
     */
    public boolean isBlocked(String key) {
        if (key == null || key.isBlank()) return false;
        Attempt a = attempts.get(normalize(key));
        if (a == null || a.lockedUntil == null) return false;
        if (Instant.now().isAfter(a.lockedUntil)) {
            // 期限切れ → 自動解除
            attempts.remove(normalize(key));
            return false;
        }
        return true;
    }

    private String normalize(String key) {
        return key.trim().toLowerCase();
    }
}
