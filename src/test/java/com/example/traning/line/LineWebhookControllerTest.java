package com.example.traning.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.traning.dao.UserDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ita4-1: {@link LineWebhookController}の署名検証・follow/unfollowイベント処理の検証。
 * UserDaoはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class LineWebhookControllerTest {

  private static final String CHANNEL_SECRET = "test-channel-secret";

  @Mock private UserDao userDao;

  private LineWebhookController controller;

  @BeforeEach
  void setUp() {
    controller = new LineWebhookController(userDao, new ObjectMapper());
    ReflectionTestUtils.setField(controller, "channelSecret", CHANNEL_SECRET);
  }

  @Test
  void 署名なしリクエストは403を返す() {
    ResponseEntity<Void> response = controller.handle(null, "{\"events\":[]}");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void 署名不一致のリクエストは403を返す() {
    ResponseEntity<Void> response = controller.handle("invalid-signature", "{\"events\":[]}");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(userDao, never()).updateLineFriendAdded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  @Test
  void 正しい署名でfollowイベントを受信するとline_friend_addedをtrueに更新する() {
    String body =
        "{\"events\":[{\"type\":\"follow\",\"source\":{\"type\":\"user\",\"userId\":\"U123\"}}]}";
    String signature = sign(body);

    ResponseEntity<Void> response = controller.handle(signature, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(userDao).updateLineFriendAdded("U123", true);
  }

  @Test
  void 正しい署名でunfollowイベントを受信するとline_friend_addedをfalseに更新する() {
    String body =
        "{\"events\":[{\"type\":\"unfollow\",\"source\":{\"type\":\"user\",\"userId\":\"U456\"}}]}";
    String signature = sign(body);

    ResponseEntity<Void> response = controller.handle(signature, body);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(userDao).updateLineFriendAdded("U456", false);
  }

  @Test
  void チャネルシークレット未設定時は署名検証せず200を返し何もしない() {
    ReflectionTestUtils.setField(controller, "channelSecret", "");

    ResponseEntity<Void> response = controller.handle("anything", "{\"events\":[]}");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(userDao, never()).updateLineFriendAdded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean());
  }

  private String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(CHANNEL_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
