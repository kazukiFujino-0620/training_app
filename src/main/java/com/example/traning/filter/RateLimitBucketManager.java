package com.example.traning.filter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.traning.config.RateLimitProperties;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * レート制限バケットの生成・キャッシュ管理。
 *
 * キー体系:
 *   "login:{ip}"        → POST /login  (IPベース)
 *   "signup:{ip}"       → POST /signup (IPベース)
 *   "api-write:{key}"   → POST/PUT/DELETE /api/** (userId or IP)
 *   "api-read:{key}"    → GET /api/**             (userId or IP)
 *
 * 将来 Redis クラスタ対応が必要になった場合はこのクラスのみ変更する。
 */
@Component
public class RateLimitBucketManager {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties props;

    public RateLimitBucketManager(RateLimitProperties props) {
        this.props = props;
    }

    public Bucket loginBucket(String ip) {
        return buckets.computeIfAbsent("login:" + ip, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(props.getLoginMaxPerMinute())
                    .refillGreedy(props.getLoginMaxPerMinute(), Duration.ofMinutes(1))
                    .build())
                .build());
    }

    public Bucket signupBucket(String ip) {
        return buckets.computeIfAbsent("signup:" + ip, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(props.getSignupMaxPerHour())
                    .refillGreedy(props.getSignupMaxPerHour(), Duration.ofHours(1))
                    .build())
                .build());
    }

    public Bucket apiWriteBucket(String key) {
        return buckets.computeIfAbsent("api-write:" + key, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(props.getApiWriteMaxPerMinute())
                    .refillGreedy(props.getApiWriteMaxPerMinute(), Duration.ofMinutes(1))
                    .build())
                .build());
    }

    public Bucket apiReadBucket(String key) {
        return buckets.computeIfAbsent("api-read:" + key, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(props.getApiReadMaxPerMinute())
                    .refillGreedy(props.getApiReadMaxPerMinute(), Duration.ofMinutes(1))
                    .build())
                .build());
    }
}
