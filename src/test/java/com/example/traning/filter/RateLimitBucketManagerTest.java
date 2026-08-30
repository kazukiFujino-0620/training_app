package com.example.traning.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * SEC-1対応: ログイン試行のレート制限が{@code refillGreedy}のままだと、約6秒に1トークンずつ
 * 継続的に補充されてしまい、数秒間隔での連続試行（人間の手打ち～緩やかな自動攻撃）ではバケットが
 * 枯渇せず永遠にブロックされない不具合があった（本番の診断ログで実測・確認済み）。
 *
 * <p>このテストは{@link RateLimitBucketManager}が実際に使っている設定
 * （容量10・{@code refillIntervally(10, 1分)}）を仮想時計付きで再現し、「規定回数消費後は次の補充
 * 区間が来るまで一切補充されない」というintervally方式の挙動を検証する。{@code RateLimitBucketManager}
 * 自体はシステム時計に固定されており実時間のsleepなしに検証できないため、同一のBandwidth設定を
 * 用いた直接検証とする。
 */
class RateLimitBucketManagerTest {

  /** テスト用の仮想時計。advance()で時間を進めるまで固定される。 */
  private static class VirtualClock implements TimeMeter {
    private final AtomicLong nanos = new AtomicLong(0);

    @Override
    public long currentTimeNanos() {
      return nanos.get();
    }

    @Override
    public boolean isWallClockBased() {
      return false;
    }

    void advance(Duration d) {
      nanos.addAndGet(d.toNanos());
    }
  }

  private Bucket loginStyleBucket(VirtualClock clock) {
    return Bucket.builder()
        .addLimit(Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(1)).build())
        .withCustomTimePrecision(clock)
        .build();
  }

  @Test
  void refillIntervally_容量分消費後は区間が来るまで一切補充されない() {
    VirtualClock clock = new VirtualClock();
    Bucket bucket = loginStyleBucket(clock);

    for (int i = 0; i < 10; i++) {
      assertThat(bucket.tryConsume(1)).as("1回目〜10回目は成功するはず").isTrue();
    }
    assertThat(bucket.tryConsume(1)).as("11回目は容量超過で失敗するはず").isFalse();

    // SEC-1で発覚した不具合の再発防止: refillGreedyなら30秒経過で約5トークン補充されてしまうが、
    // refillIntervallyでは区間(1分)が満了するまで一切補充されない。
    clock.advance(Duration.ofSeconds(30));
    assertThat(bucket.tryConsume(1))
        .as("区間の半分(30秒)しか経っていないので、まだ補充されず失敗するはず")
        .isFalse();
  }

  @Test
  void refillIntervally_区間満了後は容量まで一括で補充される() {
    VirtualClock clock = new VirtualClock();
    Bucket bucket = loginStyleBucket(clock);

    for (int i = 0; i < 10; i++) {
      bucket.tryConsume(1);
    }
    assertThat(bucket.tryConsume(1)).isFalse();

    clock.advance(Duration.ofMinutes(1));

    for (int i = 0; i < 10; i++) {
      assertThat(bucket.tryConsume(1)).as("区間満了後は再び10回消費できるはず").isTrue();
    }
    assertThat(bucket.tryConsume(1)).isFalse();
  }
}
