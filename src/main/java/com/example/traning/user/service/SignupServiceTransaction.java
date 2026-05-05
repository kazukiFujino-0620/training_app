package com.example.traning.user.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;

@Component
public class SignupServiceTransaction {

	private final UserDao userDao;

	public SignupServiceTransaction(UserDao userDao) {
		this.userDao = userDao;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean execute(SignupForm signupForm) {
		try {
			User user = new User(null, signupForm.getEmail(), signupForm.getPassword(), signupForm.getUsername(), "ROLE_USER",
					true, LocalDateTime.now(), LocalDateTime.now());

			userDao.insert(user);
			return true;
		} catch (Exception e) {
			throw e;
		}
	}
}
