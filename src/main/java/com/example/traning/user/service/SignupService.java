package com.example.traning.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.traning.user.form.SignupForm;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SignupService {
	private final SignupServiceTransaction signupTransaction;
	private final PasswordEncoder passwordEncoder;

	public SignupService(SignupServiceTransaction signupTransaction, PasswordEncoder passwordEncoder) {
		this.signupTransaction = signupTransaction;
		this.passwordEncoder = passwordEncoder;
	}

	public boolean register(SignupForm signupForm) {
		if (userInfocheck(signupForm, true) == false) {
			return false;
		}
		String encodedPassword = passwordEncoder.encode(signupForm.getPassword());
		signupForm.setPassword(encodedPassword);

		log.info("登録処理開始: " + signupForm.getEmail());
		return signupTransaction.execute(signupForm);
	}

	private static boolean userInfocheck(SignupForm signupForm, boolean checkFlg) {
		if (!signupForm.getPassword().equals(signupForm.getPassword_confirm())) {
			log.warn("パスワードが一致しません。");
			return false;
		}
		return true;
	}
}
