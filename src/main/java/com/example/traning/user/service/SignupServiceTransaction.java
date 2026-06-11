package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class SignupServiceTransaction {

  private final UserDao userDao;
  private final PasswordEncoder passwordEncoder;
  private final AccountRestoreService accountRestoreService;

  public SignupServiceTransaction(
      UserDao userDao,
      PasswordEncoder passwordEncoder,
      AccountRestoreService accountRestoreService) {
    this.userDao = userDao;
    this.passwordEncoder = passwordEncoder;
    this.accountRestoreService = accountRestoreService;
  }

  @Transactional(rollbackFor = Exception.class)
  public boolean execute(SignupForm signupForm) {
    try {
      // 論理削除済みユーザーが同じメールで再登録しようとした場合は復元フローへ
      if (userDao.selectSoftDeletedByEmail(signupForm.getEmail()).isPresent()) {
        accountRestoreService.initiateRestore(signupForm.getEmail());
        throw new AccountRestoreRequiredException();
      }

      User user = new User();
      user.setEmail(signupForm.getEmail());

      // OAuth2経由の場合は仮パスワードを生成してハッシュ化して保存する
      if (signupForm.isOAuth2Signup()) {
        String temporaryPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        log.info("OAuth2 user registered - userId will be assigned by DB");
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
      user.setHeightCm(signupForm.getHeightCm());
      user.setWeightKg(signupForm.getWeightKg());
      user.setGender(
          signupForm.getGender() != null && !signupForm.getGender().isBlank()
              ? signupForm.getGender()
              : null);
      user.setBirthDate(signupForm.getBirthDate());

      userDao.insert(user);
      log.info("User registered successfully - isOAuth2: {}", signupForm.isOAuth2Signup());
      return true;
    } catch (Exception e) {
      log.error("Failed to register user - email: {}", signupForm.getEmail(), e);
      throw e;
    }
  }
}
