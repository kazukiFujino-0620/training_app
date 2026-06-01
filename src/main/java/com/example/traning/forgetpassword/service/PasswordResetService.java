package com.example.traning.forgetpassword.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.common.MailService;
import com.example.traning.dao.UserDao;
import com.example.traning.forgetpassword.dao.PasswordResetTokenDao;
import com.example.traning.forgetpassword.entity.PasswordResetToken;
import com.example.traning.user.User;

@Service
public class PasswordResetService {

    private final PasswordResetTokenDao tokenDao;
    private final MailService mailService;
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(PasswordResetTokenDao tokenDao, MailService mailService, UserDao userDao,
            PasswordEncoder passwordEncoder) {
        this.tokenDao = tokenDao;
        this.mailService = mailService;
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createResetToken(String email) {
        tokenDao.deleteExpiredTokens(LocalDateTime.now());
        userDao.selectByEmail(email).ifPresent(user -> {

            // 同一ユーザーの既存トークンを削除（複数トークン並存を防ぐ）
            tokenDao.deleteByUserId(user.getUserId());

            // 1. トークンの生成
            String token = UUID.randomUUID().toString();

            LocalDateTime expiryDate = LocalDateTime.now().plusMinutes(30);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.token = token;
            resetToken.expiryDate = expiryDate;
            resetToken.userId = user.getUserId();

            // 3. DB保存
            tokenDao.insert(resetToken);

            // 4. コミット後にメール送信（TX内でのメール送信失敗でロールバックされる不整合を防ぐ）
            final String tokenForMail = token;
            final String emailForMail = email;
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            mailService.sendResetMail(emailForMail, tokenForMail);
                        }
                    });
        });
    }

    @Transactional
    public void updatePassword(String token, String newPassword) {
        // 1. トークンを検索（Optionalではない場合）
        PasswordResetToken resetToken = tokenDao.selectByToken(token);

        // nullチェックを行う
        if (resetToken == null) {
            throw new RuntimeException("無効なトークンです。");
        }

        // 2. 有効期限のチェック
        if (resetToken.expiryDate.isBefore(LocalDateTime.now())) {
            tokenDao.delete(resetToken);
            throw new RuntimeException("トークンの有効期限が切れています。");
        }

        // 3. ユーザーの取得とパスワード更新
        User user = userDao.selectById(resetToken.userId);

        if (user == null) {
            throw new RuntimeException("ユーザーが見つかりません。");
        }

        // パスワードをハッシュ化して更新
        User updatedUser = user.toBuilder()
                .password(passwordEncoder.encode(newPassword))
                .updatedDatetime(LocalDateTime.now())
                .build();
        userDao.update(updatedUser);

        // 4. 使用済みトークンの削除
        tokenDao.delete(resetToken);
    }
}