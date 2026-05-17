package com.example.traning.user.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SignupServiceTransaction {

	private final UserDao userDao;

	public SignupServiceTransaction(UserDao userDao) {
		this.userDao = userDao;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean execute(SignupForm signupForm) {
		try {
			User user = new User();
			user.setEmail(signupForm.getEmail());

			// OAuth2経由の場合は仮パスワードを生成
			if (signupForm.isOAuth2Signup()) {
				String temporaryPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
				user.setPassword(temporaryPassword);
				log.info("OAuth2 user registered with temporary password - email: {}", signupForm.getEmail());
			} else {
				// フォーム登録の場合は既にエンコード済みのパスワード
				user.setPassword(signupForm.getPassword());
			}

			user.setUserName(signupForm.getUsername());
			user.setRole("ROLE_USER");
			user.setEnabled(true);
			user.setGoogleId(signupForm.getGoogleId() != null ? signupForm.getGoogleId() : null);
			user.setLineId(signupForm.getLineId() != null ? signupForm.getLineId() : null);
			user.setCreateDatetime(LocalDateTime.now());
			user.setUpdatedDatetime(LocalDateTime.now());

			userDao.insert(user);
			log.info("User registered successfully - email: {}, isOAuth2: {}", signupForm.getEmail(),
					signupForm.isOAuth2Signup());
			return true;
		} catch (Exception e) {
			log.error("Failed to register user - email: {}", signupForm.getEmail(), e);
			throw e;
		}
	}
}
