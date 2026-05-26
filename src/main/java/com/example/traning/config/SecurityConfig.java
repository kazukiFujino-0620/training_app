package com.example.traning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import com.example.traning.user.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String PUBLIC_PATHS    = "/signup";
    private static final String LOGIN_PATH      = "/login";
    private static final String PASSWORD_PATH   = "/password/**";
    private static final String CSS_PATH        = "/css/**";
    private static final String JS_PATH         = "/js/**";
    private static final String IMAGES_PATH     = "/images/**";
    private static final String ADMIN_PATH      = "/admin/**";
    private static final String USER_PATH       = "/user/**";
    // Swagger UI はローカル開発専用。本番では springdoc.swagger-ui.enabled=false で無効化する。
    private static final String SWAGGER_UI_PATH  = "/swagger-ui/**";
    private static final String SWAGGER_HTML     = "/swagger-ui.html";
    private static final String API_DOCS_PATH    = "/v3/api-docs/**";
    private static final String OPENAPI_YAML     = "/openapi.yaml";

    /** 環境変数 APP_REMEMBER_ME_KEY から注入。未設定時は起動失敗させる。 */
    @Value("${app.security.remember-me-key}")
    private String rememberMeKey;

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) throws Exception {

        org.springframework.security.authentication.dao.DaoAuthenticationProvider provider =
                new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // hideUserNotFoundExceptions=true（デフォルト）でユーザー列挙攻撃を防止する。
        // エラー理由の区別はセッション属性経由で行うため機能に影響しない。
        provider.setHideUserNotFoundExceptions(true);

        http
            .authenticationProvider(provider)

            // ── セッション管理 ─────────────────────────────────────────────
            .sessionManagement(session -> session
                    // ログイン成功時にセッションIDを再生成してセッション固定化攻撃を防ぐ
                    .sessionFixation().changeSessionId()
                    // 同一ユーザーのセッションを1つに制限
                    .maximumSessions(1))

            // ── URL ベースの認可 ────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_PATHS, LOGIN_PATH, PASSWORD_PATH,
                            CSS_PATH, JS_PATH, IMAGES_PATH,
                            SWAGGER_UI_PATH, SWAGGER_HTML, API_DOCS_PATH, OPENAPI_YAML)
                    .permitAll()
                    .requestMatchers(ADMIN_PATH).hasRole("ADMIN")
                    .requestMatchers(USER_PATH).hasAnyRole("USER", "ADMIN")
                    .anyRequest().authenticated())

            // ── セキュリティヘッダー ────────────────────────────────────────
            .headers(headers -> headers
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(contentType -> {})
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; " +
                            // 'unsafe-inline' は Swagger UI のインラインスクリプトに必要。
                            // 本番では springdoc を無効化しているため実質影響なし。
                            "script-src 'self' 'unsafe-inline' 'unsafe-hashes' https://cdn.jsdelivr.net; " +
                            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                            "font-src 'self' https://fonts.gstatic.com; " +
                            "img-src 'self' data:; " +
                            "connect-src 'self'; " +
                            "frame-ancestors 'none';")))

            // ── CSRF 保護 ───────────────────────────────────────────────────
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))

            // ── OAuth2 ログイン ─────────────────────────────────────────────
            .oauth2Login(oauth2 -> oauth2
                    .loginPage(LOGIN_PATH)
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService))
                    .defaultSuccessUrl("/menu", true)
                    .failureHandler((request, response, exception) -> {
                        request.getSession().invalidate();
                        response.sendRedirect("/login?error=not_registered");
                    }))

            // ── フォームログイン ────────────────────────────────────────────
            .formLogin(login -> login
                    .loginPage(LOGIN_PATH)
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .defaultSuccessUrl("/menu", true)
                    .failureHandler((request, response, exception) -> {
                        // セッション属性からエラー理由を取得（CustomUserDetailsService が設定）
                        String reason = "bad_credentials";
                        jakarta.servlet.http.HttpSession session = request.getSession(false);
                        if (session != null) {
                            String savedReason = (String) session.getAttribute("LOGIN_ERROR_REASON");
                            if (savedReason != null) {
                                reason = savedReason;
                                session.removeAttribute("LOGIN_ERROR_REASON");
                            }
                        }
                        response.sendRedirect("/login?error&reason=" + reason);
                    })
                    .permitAll())

            // ── ログアウト ──────────────────────────────────────────────────
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl(LOGIN_PATH + "?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "remember-me-cookie")
                    .permitAll())

            // ── Remember-me ─────────────────────────────────────────────────
            // キーは環境変数 APP_REMEMBER_ME_KEY から注入（ハードコード禁止）
            .rememberMe(remember -> remember
                    .key(rememberMeKey)
                    .tokenValiditySeconds(7 * 24 * 60 * 60)
                    .rememberMeParameter("remember-me")
                    .rememberMeCookieName("remember-me-cookie"));

        return http.build();
    }

    /** セッション上限（maximumSessions）を機能させるために必要 */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
