package com.example.traning.emailchange;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.common.MailService;
import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private final EmailChangeTokenDao emailChangeTokenDao;
    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Transactional
    public void initiateChange(Long userId, String currentEmail, String newEmail, String rawPassword) {
        // パスワード照合
        User user = userDao.selectById(userId.intValue());
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("パスワードが正しくありません");
        }
        // 新メールの重複チェック
        if (userDao.selectByEmail(newEmail).isPresent()) {
            throw new IllegalArgumentException("そのメールアドレスはすでに使用されています");
        }
        // 既存トークン削除
        emailChangeTokenDao.deleteByUserId(userId);
        // トークン生成
        String token = UUID.randomUUID().toString();
        EmailChangeToken ect = new EmailChangeToken();
        ect.userId = userId;
        ect.newEmail = newEmail;
        ect.token = token;
        ect.expiryDate = LocalDateTime.now().plusHours(24);
        emailChangeTokenDao.insert(ect);
        // 確認メール送信
        mailService.sendEmailChangeMail(newEmail, token);
    }

    @Transactional
    public void confirmChange(String token) {
        Optional<EmailChangeToken> opt = emailChangeTokenDao.selectByToken(token);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("無効なトークンです");
        }
        EmailChangeToken ect = opt.get();
        if (ect.expiryDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("トークンの有効期限が切れています");
        }
        // 重複再チェック
        if (userDao.selectByEmail(ect.newEmail).isPresent()) {
            throw new IllegalArgumentException("そのメールアドレスはすでに使用されています");
        }
        // メール更新
        userDao.updateEmail(ect.userId, ect.newEmail);
        // トークン削除
        emailChangeTokenDao.deleteByUserId(ect.userId);
    }
}
