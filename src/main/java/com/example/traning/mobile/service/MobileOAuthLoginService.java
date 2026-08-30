package com.example.traning.mobile.service;

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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * モバイルアプリ向けGoogle/LINEログイン（Web版のGoogle/LINEログインボタンと同等の機能をモバイルに追加）。
 *
 * <p>{@code /api/mobile/**}はJWT認証のためステートレスに構成されており{@link jakarta.servlet.http.HttpSession}
 * が使えないため、認可フロー自体（start/callback）はこのサービスを呼び出す{@code MobileOAuthLoginController}経由で、
 * セッションが使えるデフォルトのSecurityFilterChain側（{@code /mobile-oauth/**}）で実行する。 code→トークン→プロフィール取得の実装は{@link
 * com.example.traning.line.LineAccountLinkService}のAuthorization Codeフローを踏襲している。
 */
@Slf4j
@Service
public class MobileOAuthLoginService {

  private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
  private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
  private static final String LINE_TOKEN_URL = "https://api.line.me/oauth2/v2.1/token";
  private static final String LINE_PROFILE_URL = "https://api.line.me/v2/profile";
  private static final String GOOGLE_AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String LINE_AUTHORIZE_URL = "https://access.line.me/oauth2/v2.1/authorize";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final UserDao userDao;

  @Value("${spring.security.oauth2.client.registration.google.client-id}")
  private String googleClientId;

  @Value("${spring.security.oauth2.client.registration.google.client-secret}")
  private String googleClientSecret;

  @Value("${spring.security.oauth2.client.registration.line.client-id}")
  private String lineClientId;

  @Value("${spring.security.oauth2.client.registration.line.client-secret}")
  private String lineClientSecret;

  @Value("${app.base-url}")
  private String baseUrl;

  public MobileOAuthLoginService(
      HttpClient httpClient, ObjectMapper objectMapper, UserDao userDao) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.userDao = userDao;
  }

  public String buildAuthorizeUrl(String provider, String state) {
    if ("google".equals(provider)) {
      return GOOGLE_AUTHORIZE_URL
          + "?response_type=code"
          + "&client_id="
          + encode(googleClientId)
          + "&redirect_uri="
          + encode(redirectUri(provider))
          + "&state="
          + encode(state)
          + "&scope="
          + encode("profile email");
    }
    if ("line".equals(provider)) {
      return LINE_AUTHORIZE_URL
          + "?response_type=code"
          + "&client_id="
          + encode(lineClientId)
          + "&redirect_uri="
          + encode(redirectUri(provider))
          + "&state="
          + encode(state)
          + "&scope=profile";
    }
    throw new IllegalArgumentException("未対応のプロバイダーです: " + provider);
  }

  /**
   * codeを交換して該当ユーザーを解決する。Web版（{@code CustomOAuth2UserService}）と同じ検索順（LINEはlineId優先→
   * メールにフォールバック、Googleはメール）で既存ユーザーのみを対象にする（モバイル発の新規登録は対象外）。
   *
   * @throws IllegalArgumentException 対応するユーザーが見つからない場合
   * @throws IllegalStateException トークン・プロフィール取得に失敗した場合
   */
  public User resolveUser(String provider, String code) {
    if ("google".equals(provider)) {
      return resolveGoogleUser(code);
    }
    if ("line".equals(provider)) {
      return resolveLineUser(code);
    }
    throw new IllegalArgumentException("未対応のプロバイダーです: " + provider);
  }

  private User resolveGoogleUser(String code) {
    String accessToken =
        exchangeCodeForToken(GOOGLE_TOKEN_URL, googleClientId, googleClientSecret, code, "google");
    JsonNode profile = fetchProfile(GOOGLE_USERINFO_URL, accessToken, "Googleプロフィール取得");
    String email = profile.path("email").asText(null);
    if (email == null || email.isBlank()) {
      throw new IllegalStateException("Googleアカウントのメールアドレスを取得できませんでした");
    }
    return userDao
        .selectByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("このGoogleアカウントに対応するアカウントが見つかりません"));
  }

  private User resolveLineUser(String code) {
    String accessToken =
        exchangeCodeForToken(LINE_TOKEN_URL, lineClientId, lineClientSecret, code, "line");
    JsonNode profile = fetchProfile(LINE_PROFILE_URL, accessToken, "LINEプロフィール取得");
    String lineUserId = profile.path("userId").asText(null);
    if (lineUserId == null || lineUserId.isBlank()) {
      throw new IllegalStateException("LINEユーザーIDを取得できませんでした");
    }

    Optional<User> byLineId = userDao.selectByLineId(lineUserId);
    if (byLineId.isPresent()) {
      return byLineId.get();
    }
    throw new IllegalArgumentException("このLINEアカウントに対応するアカウントが見つかりません");
  }

  private String redirectUri(String provider) {
    return baseUrl + "/mobile-oauth/" + provider + "/callback";
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String exchangeCodeForToken(
      String tokenUrl, String clientId, String clientSecret, String code, String provider) {
    String form =
        "grant_type=authorization_code"
            + "&code="
            + encode(code)
            + "&redirect_uri="
            + encode(redirectUri(provider))
            + "&client_id="
            + encode(clientId)
            + "&client_secret="
            + encode(clientSecret);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();

    JsonNode json = send(request, provider + "トークン取得");
    return json.path("access_token").asText();
  }

  private JsonNode fetchProfile(String profileUrl, String accessToken, String actionLabel) {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(profileUrl))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();
    return send(request, actionLabel);
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
