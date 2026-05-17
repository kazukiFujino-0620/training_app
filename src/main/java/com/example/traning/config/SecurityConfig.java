package com.example.traning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.example.traning.user.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // ★ 追加: @PreAuthorize/@PostAuthorize をメソッドレベルで有効化
public class SecurityConfig {

	private static final String PUBLIC_PATHS = "/signup";
	private static final String LOGIN_PATH = "/login";
	private static final String PASSWORD_PATH = "/password/**";
	private static final String CSS_PATH = "/css/**";
	private static final String JS_PATH = "/js/**";
	private static final String IMAGES_PATH = "/images/**";
	private static final String ADMIN_PATH = "/admin/**";
	private static final String USER_PATH = "/user/**";

	private final CustomOAuth2UserService customOAuth2UserService;

	public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
		this.customOAuth2UserService = customOAuth2UserService;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// ── URL ベースの認可 ─────────────────────────────────────
				.authorizeHttpRequests(auth -> auth
						// 公開ページ・静的リソース
						.requestMatchers(PUBLIC_PATHS, LOGIN_PATH, PASSWORD_PATH,
								CSS_PATH, JS_PATH, IMAGES_PATH)
						.permitAll()
						// 管理者専用
						.requestMatchers(ADMIN_PATH).hasRole("ADMIN")
						// 一般ユーザー以上
						.requestMatchers(USER_PATH).hasAnyRole("USER", "ADMIN")
						// その他はすべて認証必須
						.anyRequest().authenticated())

				// ── セキュリティヘッダー ──────────────────────────────────
				// Spring Boot 3.x (Spring Security 6.x) では xssProtection().enable() は廃止。
				// X-XSS-Protection ヘッダー自体もモダンブラウザでは非推奨のため、
				// CSP (Content-Security-Policy) で代替する。
				.headers(headers -> headers
						// HSTS（HTTP Strict-Transport-Security）設定
						// HTTPS通信を強制し、中間者攻撃を防止
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000)) // 1年間
						// クリックジャッキング対策: iframe 埋め込みを全面禁止
						.frameOptions(frame -> frame.deny())
						// MIME スニッフィング対策
						.contentTypeOptions(contentType -> {
						})
						// CSP: 自ドメイン + 利用中の外部リソースのみ許可
						// ※ Thymeleaf の inline スタイルを使用している箇所があるため
						// style-src に 'unsafe-inline' を暫定許可。
						// 将来的に nonce/hash 方式へ移行することを推奨。
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; " +
										"script-src 'self' 'unsafe-inline' 'unsafe-hashes' https://cdn.jsdelivr.net; " +
										"style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
										"font-src 'self' https://fonts.gstatic.com; " +
										"img-src 'self' data:; " +
										"connect-src 'self'; " +
										"frame-ancestors 'none';")))

				// ── CSRF 保護 ────────────────────────────────────
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
				// ── OAuth2 ログイン ───────────────────────────────────────
				.oauth2Login(oauth2 -> oauth2
						.loginPage(LOGIN_PATH)
						.userInfoEndpoint(userInfo -> userInfo
								.userService(customOAuth2UserService))
						.defaultSuccessUrl("/menu", true)
						.failureHandler((request, response, exception) -> {
							request.getSession().invalidate();
							response.sendRedirect("/login?error=not_registered");
						}))

				// ── フォームログイン ──────────────────────────────────────
				.formLogin(login -> login
						.loginPage("/login")
						.usernameParameter("username")
						.passwordParameter("password")
						.defaultSuccessUrl("/menu", true)
						.permitAll())

				// ── ログアウト ────────────────────────────────────────────
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl(LOGIN_PATH + "?logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.permitAll());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}