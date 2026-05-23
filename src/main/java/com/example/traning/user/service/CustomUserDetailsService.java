package com.example.traning.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

	private final UserDao userDao;

	public CustomUserDetailsService(UserDao userDao) {
		this.userDao = userDao;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		log.info("Form login attempt - email: {}", email);
		User user = userDao.selectByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりませんでした: " + email));

		if (Boolean.compare(user.getEnabled(), false) == 0) {
			String msg = "このメールアドレスは退会済みです: " + email +
					" 再度登録する場合は、管理者に連絡ください。";

			// 1. ログに出力
			log.error(msg);

			saveLoginErrorReasonToSession("withdrawn");

			throw new UsernameNotFoundException(msg);
		}

		// OAuth2ユーザーはパスワードがないため、フォームログインできない
		if (user.getPassword() == null || user.getPassword().isEmpty()) {
			String msg = "このユーザーはOAuth2経由で登録されています。LINEまたはGoogleでログインしてください: " + email;
			log.warn(msg);
			saveLoginErrorReasonToSession("oauth2_user");
			throw new UsernameNotFoundException(msg);
		}

		log.info("User found for form login - email: {}, role: {}", user.getEmail(), user.getRole());
		String roleName = user.getRole().replace("ROLE_", "");

		UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
				.password(user.getPassword())
				.roles(roleName)
				.build();

		log.info("UserDetails created for form login - username: {}, authorities: {}",
				userDetails.getUsername(), userDetails.getAuthorities());

		return userDetails;
	}

	private void saveLoginErrorReasonToSession(String reason) {
		try {
			org.springframework.web.context.request.ServletRequestAttributes attributes = (org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder
					.currentRequestAttributes();
			if (attributes != null) {
				jakarta.servlet.http.HttpSession session = attributes.getRequest().getSession(true);
				session.setAttribute("LOGIN_ERROR_REASON", reason);
			}
		} catch (Exception e) {
			log.error("Failed to save login error reason to session", e);
		}
	}
}
