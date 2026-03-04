package com.example.traning.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.traning.form.SignupForm;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SignupService {

//	private final UserRepository userRepository;
	// private static final Logger log =
	// LoggerFactory.getLogger(UserController.class);
	private final SignupServiceTransaction signupTransaction;
	private final PasswordEncoder passwordEncoder;

	public SignupService(SignupServiceTransaction signupTransaction, PasswordEncoder passwordEncoder) {
		this.signupTransaction = signupTransaction;
		this.passwordEncoder = passwordEncoder;
	}

	public boolean register(SignupForm signupForm) {

		// 1.入力値チェック
		if (userInfocheck(signupForm, true) == false) {
			return false;
		}
		// 2.パスワードの暗号化
		String encodedPassword = passwordEncoder.encode(signupForm.getPassword());

		// 3.登録情報をsignupFormに格納
		signupForm.setPassword(encodedPassword);

		// 4.ユーザー登録
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
