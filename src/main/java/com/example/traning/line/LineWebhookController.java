package com.example.traning.line;

import com.example.traning.dao.UserDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE公式アカウントの友だち追加/解除（follow/unfollow）Webhookを受信する（ita4-1）。
 *
 * <p>{@code line_friend_added} を更新し、週次・月次サマリーバッチでLINE通知可否の判定に使う。
 * チャネルシークレット未設定時（LINE公式アカウント未開設の間）は署名検証をスキップしようがないため200のみ返し何もしない。
 */
@Slf4j
@RestController
@RequestMapping("/webhook/line")
@RequiredArgsConstructor
public class LineWebhookController {

  private final UserDao userDao;
  private final ObjectMapper objectMapper;

  @Value("${line.messaging.channel-secret:}")
  private String channelSecret;

  @PostMapping
  public ResponseEntity<Void> handle(
      @RequestHeader(value = "X-Line-Signature", required = false) String signature,
      @RequestBody String rawBody) {

    if (channelSecret == null || channelSecret.isBlank()) {
      log.warn("LINEチャネルシークレット未設定のためWebhookイベントを処理せず200を返します");
      return ResponseEntity.ok().build();
    }
    if (!isValidSignature(rawBody, signature)) {
      log.warn("LINE Webhookの署名検証に失敗しました");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      JsonNode events = objectMapper.readTree(rawBody).path("events");
      for (JsonNode event : events) {
        String type = event.path("type").asText();
        String lineUserId = event.path("source").path("userId").asText(null);
        if (lineUserId == null) {
          continue;
        }
        if ("follow".equals(type)) {
          userDao.updateLineFriendAdded(lineUserId, true);
          log.info("LINE友だち追加を検知しました");
        } else if ("unfollow".equals(type)) {
          userDao.updateLineFriendAdded(lineUserId, false);
          log.info("LINE友だち解除/ブロックを検知しました");
        }
      }
    } catch (Exception e) {
      // Webhookはイベント処理の成否によらず200を返すのがLINE側の推奨仕様のため、ここでは例外を外に投げない
      log.error("LINE Webhookイベントの処理中にエラーが発生しました", e);
    }

    return ResponseEntity.ok().build();
  }

  boolean isValidSignature(String body, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      String expected = Base64.getEncoder().encodeToString(hash);
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.error("LINE Webhook署名検証中にエラーが発生しました", e);
      return false;
    }
  }
}
