package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.TokenResponse;
import com.example.traning.mobile.service.MobileAuthService;
import com.example.traning.mobile.service.MobileOAuthLoginService;
import com.example.traning.user.User;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイルアプリのGoogle/LINEログイン（Web版のログインボタンと同等の機能）。
 *
 * <p>{@code /api/mobile/**}はJWT認証のためステートレスに構成されており認可フローのstateをセッションに
 * 保持できないため、あえて{@code /api/mobile/**}の外（デフォルトのセッションベースSecurityFilterChain側）に
 * 配置している。モバイルアプリはシステムブラウザ（expo-web-browserの認証セッション）でこのエンドポイントを開き、
 * 完了後はカスタムURLスキーム（{@code trainingapp://oauth-callback}）へリダイレクトされる。
 *
 * <p>事前準備（本番反映時に別途必要）: Google Cloud Console / LINE Developersコンソール側で
 * {@code {baseUrl}/mobile-oauth/google/callback} ・ {@code {baseUrl}/mobile-oauth/line/callback}
 * を許可リダイレクトURIとして登録しておくこと。
 */
@Slf4j
@RestController
public class MobileOAuthLoginController {

  private static final String STATE_SESSION_PREFIX = "mobile_oauth_state_";
  private static final String DEVICE_ID_SESSION_PREFIX = "mobile_oauth_device_";
  private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "line");
  private static final String APP_SCHEME_CALLBACK = "trainingapp://oauth-callback";

  private final MobileOAuthLoginService oAuthLoginService;
  private final MobileAuthService mobileAuthService;

  @Value("${app.base-url}")
  private String baseUrl;

  public MobileOAuthLoginController(
      MobileOAuthLoginService oAuthLoginService, MobileAuthService mobileAuthService) {
    this.oAuthLoginService = oAuthLoginService;
    this.mobileAuthService = mobileAuthService;
  }

  @GetMapping("/mobile-oauth/{provider}/start")
  public void start(
      @PathVariable String provider,
      @RequestParam String deviceId,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    if (!SUPPORTED_PROVIDERS.contains(provider)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    String state = UUID.randomUUID().toString();
    session.setAttribute(STATE_SESSION_PREFIX + provider, state);
    session.setAttribute(DEVICE_ID_SESSION_PREFIX + provider, deviceId);
    response.sendRedirect(oAuthLoginService.buildAuthorizeUrl(provider, state));
  }

  @AuditLog(action = "MOBILE_OAUTH_LOGIN", targetTable = "mobile_refresh_tokens")
  @GetMapping("/mobile-oauth/{provider}/callback")
  public void callback(
      @PathVariable String provider,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    if (!SUPPORTED_PROVIDERS.contains(provider)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    Object expectedState = session.getAttribute(STATE_SESSION_PREFIX + provider);
    Object deviceId = session.getAttribute(DEVICE_ID_SESSION_PREFIX + provider);
    session.removeAttribute(STATE_SESSION_PREFIX + provider);
    session.removeAttribute(DEVICE_ID_SESSION_PREFIX + provider);

    if (error != null) {
      redirectWithError(response, "cancelled");
      return;
    }
    if (expectedState == null || !expectedState.equals(state) || deviceId == null) {
      redirectWithError(response, "invalid_state");
      return;
    }

    try {
      User user = oAuthLoginService.resolveUser(provider, code);
      TokenResponse tokens = mobileAuthService.loginViaOAuth(user, (String) deviceId);
      redirectWithTokens(response, tokens);
    } catch (IllegalArgumentException e) {
      log.warn("モバイルOAuthログイン失敗（未登録/無効） - provider: {}, reason: {}", provider, e.getMessage());
      redirectWithError(response, "not_registered");
    } catch (IllegalStateException e) {
      log.error("モバイルOAuthログイン失敗（外部API） - provider: {}", provider, e);
      redirectWithError(response, "provider_error");
    }
  }

  private void redirectWithTokens(HttpServletResponse response, TokenResponse tokens)
      throws IOException {
    StringBuilder url = new StringBuilder(APP_SCHEME_CALLBACK).append("?");
    if (tokens.isMfaRequired()) {
      url.append("mfaRequired=true&mfaTempToken=").append(encode(tokens.getMfaTempToken()));
    } else {
      url.append("accessToken=")
          .append(encode(tokens.getAccessToken()))
          .append("&refreshToken=")
          .append(encode(tokens.getRefreshToken()))
          .append("&expiresIn=")
          .append(tokens.getExpiresIn());
    }
    response.sendRedirect(url.toString());
  }

  private void redirectWithError(HttpServletResponse response, String errorCode) throws IOException {
    response.sendRedirect(APP_SCHEME_CALLBACK + "?error=" + encode(errorCode));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
