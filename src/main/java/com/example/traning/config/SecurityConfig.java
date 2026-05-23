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
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			org.springframework.security.core.userdetails.UserDetailsService userDetailsService) throws Exception {

		// ── ユーザー非表示設定の解除（本物の例外メッセージを直通させる） ──
		org.springframework.security.authentication.dao.DaoAuthenticationProvider provider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
		provider.setUserDetailsService(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder());
		// ★ これが重要！「ユーザーが見つからない」系の例外メッセージを隠さずにそのまま後ろへ流す設定です
		provider.setHideUserNotFoundExceptions(false);

		http
				// ★ 上で作ったプロバイダーを登録
				.authenticationProvider(provider)

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
				.headers(headers -> headers
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.frameOptions(frame -> frame.deny())
						.contentTypeOptions(contentType -> {
						})
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; " +
										"script-src 'self' 'unsafe-hashes' https://cdn.jsdelivr.net; " +
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
						.loginPage(LOGIN_PATH)
						.usernameParameter("username")
						.passwordParameter("password")
						.defaultSuccessUrl("/menu", true)
						.failureHandler((request, response, exception) -> {
							String reason = "bad_credentials";

							// ★ 修正ポイント: Springの例外オブジェクトは無視して、セッションから直接理由を回収する
							jakarta.servlet.http.HttpSession session = request.getSession(false);
							if (session != null) {
								String savedReason = (String) session.getAttribute("LOGIN_ERROR_REASON");
								if (savedReason != null) {
									reason = savedReason;
									// 次回のログイン時のために、使い終わったフラグは消しておく
									session.removeAttribute("LOGIN_ERROR_REASON");
								}
							}

							// 原因に応じたパラメータを付与してリダイレクト
							response.sendRedirect("/login?error&reason=" + reason);
						})
						.permitAll())

				// ── ログアウト ────────────────────────────────────────────
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessUrl(LOGIN_PATH + "?logout")
						.invalidateHttpSession(true)
						.deleteCookies("JSESSIONID")
						.permitAll())

				// ── Remember-me 機能 ──────────────────────────────────
				.rememberMe(remember -> remember
						.key("TrainingApp-SecureKey-2025")
						.tokenValiditySeconds(14 * 24 * 60 * 60)
						.rememberMeParameter("remember-me")
						.rememberMeCookieName("remember-me-cookie"));

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}