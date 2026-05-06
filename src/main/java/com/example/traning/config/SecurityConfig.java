package com.example.traning.config;

import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private CustomOAuth2UserService customOAuth2UserService;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				// 公開ページと静的リソース
				.requestMatchers("/signup", "/login", "/password/**", "/css/**", "/js/**", "/images/**").permitAll()
				// 権限が必要なページ
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
				.anyRequest().authenticated())
				// Googleログインの設定
				.oauth2Login(oauth2 -> oauth2
						.loginPage("/login")
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
						.logoutSuccessUrl("/login?logout")
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
