package com.example.traning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth.requestMatchers("/signup", "/login", "/css/**", "/js/**").permitAll()
				.anyRequest().authenticated())
				.formLogin(login -> login.loginPage("/login").usernameParameter("username")
						.passwordParameter("password").defaultSuccessUrl("/menu", true).permitAll())
				.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true).deleteCookies("JSESSIONID").permitAll());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
