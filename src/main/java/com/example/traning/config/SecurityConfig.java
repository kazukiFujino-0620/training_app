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
                        OPENAPI_YAML)
                    .permitAll()
                    .requestMatchers(ADMIN_PATH)
                    .hasRole("ADMIN")
                    .requestMatchers(USER_PATH)
                    .hasAnyRole("USER", "ADMIN")
                    .requestMatchers("/auth/mfa", "/auth/mfa/verify")
                    .authenticated()
                    .anyRequest()
                    .authenticated())

        // ── セキュリティヘッダー ────────────────────────────────────────
        .headers(
            headers ->
                headers
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
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
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))

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
