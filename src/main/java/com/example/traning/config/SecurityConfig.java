package com.example.traning.config;

import com.example.traning.filter.JwtAuthenticationFilter;
import com.example.traning.filter.RateLimitFilter;
import com.example.traning.mfa.CustomAuthenticationSuccessHandler;
import com.example.traning.mfa.MfaPendingFilter;
import com.example.traning.user.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String PUBLIC_PATHS = "/signup";
  // ita4-3: トレーナー用登録ルート（招待コード必須の別フォーム）。/signupとは別に許可が必要。
  private static final String TRAINER_SIGNUP_PATH = "/signup/trainer";
  // ita3-3: 登録LP（アプリ紹介・招待コード入力）。未ログインでの閲覧が前提のため許可が必要。
  private static final String WELCOME_PATH = "/welcome";
  // App Store/Google Play審査対応: プライバシーポリシーは未ログインでも閲覧可能にする必要がある。
  private static final String PRIVACY_PATH = "/privacy";
  private static final String LOGIN_PATH = "/login";
  private static final String PASSWORD_PATH = "/password/**";
  private static final String RESTORE_PATH = "/account/restore/**";
  private static final String CSS_PATH = "/css/**";
  private static final String JS_PATH = "/js/**";
  private static final String IMAGES_PATH = "/images/**";
  private static final String ICONS_PATH = "/icons/**";
  private static final String SW_PATH = "/sw.js";
  private static final String MANIFEST_PATH = "/manifest.json";
  private static final String ADMIN_PATH = "/admin/**";
  private static final String USER_PATH = "/user/**";
  // Swagger UI はローカル開発専用。本番では springdoc.swagger-ui.enabled=false で無効化する。
  private static final String SWAGGER_UI_PATH = "/swagger-ui/**";
  private static final String SWAGGER_HTML = "/swagger-ui.html";
  private static final String API_DOCS_PATH = "/v3/api-docs/**";
  private static final String OPENAPI_YAML = "/openapi.yaml";
  // LINE Messaging APIからのWebhook。署名検証（X-Line-Signature）はLineWebhookController側で行うため未認証で受ける。
  private static final String LINE_WEBHOOK_PATH = "/webhook/line";
  // モバイルアプリのGoogle/LINEログイン（未認証状態から開始するため許可が必要）。/api/mobile/**とは別に
  // セッションが使えるこちら側のチェーンに置く（MobileOAuthLoginController参照）。
  private static final String MOBILE_OAUTH_PATH = "/mobile-oauth/**";

  /** 環境変数 APP_REMEMBER_ME_KEY から注入。未設定時は起動失敗させる。 */
  @Value("${app.security.remember-me-key}")
  private String rememberMeKey;

  private final CustomOAuth2UserService customOAuth2UserService;

  public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
    this.customOAuth2UserService = customOAuth2UserService;
  }

  /**
   * モバイルAPI用 SecurityFilterChain（優先度1）。 /api/mobile/** のみを対象にJWT認証・ステートレスで処理する。
   * 既存のWebセッション認証とは完全に独立している。
   */
  @Bean
  @Order(1)
  public SecurityFilterChain mobileSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {

    http.securityMatcher("/api/mobile/**")
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/mobile/auth/login",
                        "/api/mobile/auth/refresh",
                        "/api/mobile/auth/mfa/verify")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      res.setContentType("application/json;charset=UTF-8");
                      res.getWriter().write("{\"error\":\"Unauthorized\"}");
                    }))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      RateLimitFilter rateLimitFilter,
      CustomAuthenticationSuccessHandler mfaSuccessHandler,
      MfaPendingFilter mfaPendingFilter)
      throws Exception {

    org.springframework.security.authentication.dao.DaoAuthenticationProvider provider =
        new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    // hideUserNotFoundExceptions=true（デフォルト）でユーザー列挙攻撃を防止する。
    // エラー理由の区別はセッション属性経由で行うため機能に影響しない。
    provider.setHideUserNotFoundExceptions(true);

    http.authenticationProvider(provider)

        // ── セッション管理 ─────────────────────────────────────────────
        .sessionManagement(
            session ->
                session
                    // ログイン成功時にセッションIDを再生成してセッション固定化攻撃を防ぐ
                    .sessionFixation()
                    .changeSessionId()
                    // 同一ユーザーのセッションを1つに制限
                    .maximumSessions(1))

        // ── 未認証時の応答（AJAX → 401、ブラウザ → /login リダイレクト） ──
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                      } else {
                        response.sendRedirect("/login");
                      }
                    }))

        // ── URL ベースの認可 ────────────────────────────────────────────
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        PUBLIC_PATHS,
                        TRAINER_SIGNUP_PATH,
                        WELCOME_PATH,
                        PRIVACY_PATH,
                        LOGIN_PATH,
                        PASSWORD_PATH,
                        RESTORE_PATH,
                        CSS_PATH,
                        JS_PATH,
                        IMAGES_PATH,
                        ICONS_PATH,
                        SW_PATH,
                        MANIFEST_PATH,
                        SWAGGER_UI_PATH,
                        SWAGGER_HTML,
                        API_DOCS_PATH,
                        OPENAPI_YAML,
                        LINE_WEBHOOK_PATH,
                        MOBILE_OAUTH_PATH)
                    .permitAll()
                    .requestMatchers(ADMIN_PATH)
                    // ita1-1 マルチテナント化 フェーズ3: ORG_ADMIN/STORE_ADMIN も /admin/** に到達可能とする。
                    // 実際のデータアクセス範囲は OrganizationScopeResolver +
                    // 各コントローラーの @PreAuthorize / IDOR チェックで絞り込む。
                    .hasAnyRole("ADMIN", "ORG_ADMIN", "STORE_ADMIN")
                    .requestMatchers(USER_PATH)
                    // ORG_ADMIN/STORE_ADMINも自分自身のプロフィール・通知設定（LINE連携含む）を
                    // 利用できる必要があるため許可する（ita4-1結合試験で発見）。
                    .hasAnyRole("USER", "ADMIN", "ORG_ADMIN", "STORE_ADMIN")
                    .requestMatchers("/auth/mfa", "/auth/mfa/verify")
                    .authenticated()
                    .anyRequest()
                    .authenticated())

        // ── セキュリティヘッダー ────────────────────────────────────────
        .headers(
            headers ->
                headers
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.includeSubDomains(true).maxAgeInSeconds(31536000).preload(true))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(contentType -> {})
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(
                        permissions ->
                            permissions
                                // 機能の最小権限化（不要なブラウザ機能をすべて拒否）
                                .policy(
                                "camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=(), fullscreen=(self)"))
                    .crossOriginOpenerPolicy(
                        coop ->
                            coop.policy(
                                org.springframework.security.web.header.writers
                                    .CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy
                                    .SAME_ORIGIN))
                    .crossOriginResourcePolicy(
                        corp ->
                            corp.policy(
                                org.springframework.security.web.header.writers
                                    .CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy
                                    .SAME_ORIGIN))
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; "
                                    +
                                    // 'unsafe-inline' は Swagger UI のインラインスクリプトに必要。
                                    // 本番では springdoc を無効化しているため実質影響なし。
                                    "script-src 'self' 'unsafe-inline' 'unsafe-hashes' https://cdn.jsdelivr.net; "
                                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                    + "font-src 'self' https://fonts.gstatic.com; "
                                    + "img-src 'self' data:; "
                                    + "connect-src 'self'; "
                                    + "worker-src 'self'; "
                                    + "form-action 'self'; "
                                    + "base-uri 'self'; "
                                    + "object-src 'none'; "
                                    + "frame-ancestors 'none';")))

        // ── CSRF 保護 ───────────────────────────────────────────────────
        // LINE Webhookは外部サーバーからのPOSTでCSRFトークンを持たないため、署名検証（Controller側）を条件に除外する
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(LINE_WEBHOOK_PATH))

        // ── OAuth2 ログイン ─────────────────────────────────────────────
        .oauth2Login(
            oauth2 ->
                oauth2
                    .loginPage(LOGIN_PATH)
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .defaultSuccessUrl("/menu", true)
                    .failureHandler(
                        (request, response, exception) -> {
                          request.getSession().invalidate();
                          response.sendRedirect("/login?error=not_registered");
                        }))

        // ── フォームログイン ────────────────────────────────────────────
        .formLogin(
            login ->
                login
                    .loginPage(LOGIN_PATH)
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .successHandler(mfaSuccessHandler)
                    .failureHandler(
                        (request, response, exception) -> {
                          // セッション属性からエラー理由を取得（CustomUserDetailsService が設定）
                          String reason = "bad_credentials";
                          jakarta.servlet.http.HttpSession session = request.getSession(false);
                          if (session != null) {
                            String savedReason =
                                (String) session.getAttribute("LOGIN_ERROR_REASON");
                            if (savedReason != null) {
                              reason = savedReason;
                              session.removeAttribute("LOGIN_ERROR_REASON");
                            }
                          }
                          response.sendRedirect("/login?error&reason=" + reason);
                        })
                    .permitAll())

        // ── ログアウト ──────────────────────────────────────────────────
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl(LOGIN_PATH + "?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "remember-me-cookie")
                    .permitAll())

        // ── Remember-me ─────────────────────────────────────────────────
        // キーは環境変数 APP_REMEMBER_ME_KEY から注入（ハードコード禁止）
        .rememberMe(
            remember ->
                remember
                    .key(rememberMeKey)
                    .tokenValiditySeconds(7 * 24 * 60 * 60)
                    .rememberMeParameter("remember-me")
                    .rememberMeCookieName("remember-me-cookie"));

    // ── レート制限フィルター ────────────────────────────────────────────
    http.addFilterBefore(rateLimitFilter, org.springframework.security.web.csrf.CsrfFilter.class);

    // ── 2FA ペンディングフィルター ────────────────────────────────────
    // CsrfFilter より後に配置して認証済みリクエストのMFA完了前をブロックする
    http.addFilterAfter(mfaPendingFilter, org.springframework.security.web.csrf.CsrfFilter.class);

    return http.build();
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
      JwtAuthenticationFilter filter) {
    FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<MfaPendingFilter> mfaPendingFilterRegistration(
      MfaPendingFilter filter) {
    FilterRegistrationBean<MfaPendingFilter> reg = new FilterRegistrationBean<>(filter);
    reg.setEnabled(false);
    return reg;
  }

  /** セッション上限（maximumSessions）を機能させるために必要 */
  @Bean
  public HttpSessionEventPublisher httpSessionEventPublisher() {
    return new HttpSessionEventPublisher();
  }
}
