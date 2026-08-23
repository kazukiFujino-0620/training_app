package com.example.traning.line;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 既存ユーザー（メール/Googleログイン等）にLINEアカウントを後付けで連携する（ita4-1 残作業）。
 *
 * <p>Spring Securityの{@code oauth2Login}はログイン専用（成功するとセッションの認証がLINE側の {@link User}に差し替わる）ため、ログイン中ユーザーを維持したまま連携するにはSpring
 * Security機構を使わず、Authorization Codeフローを手動で実行する（{@link #buildAuthorizeUrl}
 * で認可URLを組み立て、コールバックで受け取った{@code code}を{@link #completeLink}でトークン・プロフィールに交換する）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineAccountLinkService {

  private static final String AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";
  private static final String TOKEN_URL = "https://api.line.me/oauth2/v2.1/token";
  private static final String PROFILE_URL = "https://api.line.me/v2/profile";
  private static final String CALLBACK_PATH = "/user/notifications/line/callback";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final UserDao userDao;

  @Value("${spring.security.oauth2.client.registration.line.client-id}")
  private String clientId;

  @Value("${spring.security.oauth2.client.registration.line.client-secret}")
  private String clientSecret;

  @Value("${app.base-url}")
  private String baseUrl;

  public String buildAuthorizeUrl(String state) {
    return AUTHORIZE_URL
        + "?response_type=code"
        + "&client_id="
        + encode(clientId)
        + "&redirect_uri="
        + encode(redirectUri())
        + "&state="
        + encode(state)
        + "&scope=profile";
  }

  /**
   * @throws IllegalStateException トークン取得・プロフィール取得に失敗した場合
   * @throws IllegalArgumentException 取得したLINEアカウントが既に別ユーザーに連携済みの場合
   */
  @Transactional
  public void completeLink(Integer userId, String code) {
    String accessToken = exchangeCodeForToken(code);
    String lineUserId = fetchLineUserId(accessToken);

    userDao
        .selectByLineId(lineUserId)
        .ifPresent(
            existing -> {
              if (!existing.getUserId().equals(userId)) {
                throw new IllegalArgumentException("このLINEアカウントは既に別のアカウントと連携されています");
              }
            });

    userDao.updateLineId(userId, lineUserId);
    log.info("LINEアカウント連携が完了しました - userId: {}", userId);
  }

  private String redirectUri() {
    return baseUrl + CALLBACK_PATH;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String exchangeCodeForToken(String code) {
    String form =
        "grant_type=authorization_code"
            + "&code="
            + encode(code)
            + "&redirect_uri="
            + encode(redirectUri())
            + "&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

    JsonNode json = send(request, "LINEトークン取得");
    return json.path("access_token").asText();
  }

  private String fetchLineUserId(String accessToken) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(PROFILE_URL))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

    JsonNode json = send(request, "LINEプロフィール取得");
    return json.path("userId").asText();
  }

  private JsonNode send(HttpRequest request, String actionLabel) {
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            actionLabel + "に失敗しました: status=" + response.statusCode() + " body=" + response.body());
      }
      return objectMapper.readTree(response.body());
    } catch (IOException e) {
      throw new IllegalStateException(actionLabel + "に失敗しました", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(actionLabel + "が中断されました", e);
    }
  }
}
