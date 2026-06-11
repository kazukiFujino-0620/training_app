package com.example.traning.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IPアドレス単位のブルートフォース攻撃対策。
 *
 * <p>- ログイン失敗 MAX_ATTEMPTS 回 → BLOCK_DURATION ブロック - ログイン成功で即時カウンタリセット - 単一インスタンス前提（将来クラスタ化時は Redis
 * などへ移行）
 */
@Service
@Slf4j
public class IpBlockService {

  private static final int MAX_ATTEMPTS = 20;
  private static final Duration BLOCK_DURATION = Duration.ofMinutes(30);

  private static class Attempt {
    int count;
    Instant blockedUntil;
  }

  private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

  /** ログイン失敗を記録。閾値到達でブロック開始。 */
  public void recordFailure(String ip) {
    if (ip == null || ip.isBlank()) return;
    attempts.compute(
        ip,
        (k, v) -> {
          Attempt a = (v == null) ? new Attempt() : v;
          a.count++;
          if (a.count >= MAX_ATTEMPTS) {
            a.blockedUntil = Instant.now().plus(BLOCK_DURATION);
            log.warn("IP blocked due to {} failed login attempts: ip={}", MAX_ATTEMPTS, maskIp(ip));
          }
          return a;
        });
  }

  /** ログイン成功時にカウンタをリセット。 */
  public void recordSuccess(String ip) {
    if (ip == null || ip.isBlank()) return;
    attempts.remove(ip);
  }

  /** ブロック中かどうかを確認。期限切れは自動解除。 */
  public boolean isBlocked(String ip) {
    if (ip == null || ip.isBlank()) return false;
    Attempt a = attempts.get(ip);
    if (a == null || a.blockedUntil == null) return false;
    if (Instant.now().isAfter(a.blockedUntil)) {
      attempts.remove(ip);
      return false;
    }
    return true;
  }

  private String maskIp(String ip) {
    int lastDot = ip.lastIndexOf('.');
    return lastDot > 0 ? ip.substring(0, lastDot) + ".***" : ip;
  }
}
