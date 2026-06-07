package com.example.traning.user.service;

import com.example.traning.user.form.SignupForm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SignupService {
  private final SignupServiceTransaction signupTransaction;
  private final PasswordEncoder passwordEncoder;

  public SignupService(
      SignupServiceTransaction signupTransaction, PasswordEncoder passwordEncoder) {
    this.signupTransaction = signupTransaction;
    this.passwordEncoder = passwordEncoder;
  }

  public boolean register(SignupForm signupForm) {
    if (!userInfocheck(signupForm)) {
      return false;
    }
    String encodedPassword = passwordEncoder.encode(signupForm.getPassword());
    signupForm.setPassword(encodedPassword);

    log.debug("登録処理開始");
    return signupTransaction.execute(signupForm);
  }

  private static boolean userInfocheck(SignupForm signupForm) {
    if (!signupForm.getPassword().equals(signupForm.getPassword_confirm())) {
      log.warn("パスワードが一致しません。");
      return false;
    }
    return true;
  }
}
