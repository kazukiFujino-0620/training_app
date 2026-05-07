package com.example.traning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.example.traning.user.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
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
		http.authorizeHttpRequests(auth -> auth
				// 公開ページと静的リソース
				.requestMatchers(PUBLIC_PATHS, LOGIN_PATH, PASSWORD_PATH, CSS_PATH, JS_PATH, IMAGES_PATH).permitAll()
				// 権限が必要なページ
				.requestMatchers(ADMIN_PATH).hasRole("ADMIN")
				.requestMatchers(USER_PATH).hasAnyRole("USER", "ADMIN")
				.anyRequest().authenticated())
				// Googleログインの設定
				.oauth2Login(oauth2 -> oauth2
						.loginPage(LOGIN_PATH)
						.userInfoEndpoint(userInfo -> userInfo
								.userService(customOAuth2UserService))
						.defaultSuccessUrl("/menu", true)
						// ここで失敗時の処理を明示する
						.failureHandler((request, response, exception) -> {
							request.getSession().invalidate();
							response.sendRedirect("/login?error=not_registered");
						}))
				// フォームログインの設定
				.formLogin(login -> login
						.loginPage("/login")
						.usernameParameter("username")
						.passwordParameter("password")
						.defaultSuccessUrl("/menu", true)
						.permitAll())
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
