package com.example.traning.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.UserDao;
import com.example.traning.entity.User;
import com.example.traning.form.SignupForm;

@Component
public class SignupServiceTransaction {

	private final UserDao userDao; // UserRepositoryではなくUserDao

	public SignupServiceTransaction(UserDao userDao) {
		this.userDao = userDao;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean execute(SignupForm signupForm) {
		try {
			User user = new User(null, // userId (自動採番)
					signupForm.getEmail(), signupForm.getPassword(), signupForm.getUsername(), "ROLE_USER", true,
					LocalDateTime.now(), LocalDateTime.now());

			userDao.insert(user);
			return true;
		} catch (Exception e) {
			// エラーログなど
			throw e;
		}
	}
}