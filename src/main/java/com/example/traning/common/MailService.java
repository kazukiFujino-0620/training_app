package com.example.traning.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendResetMail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("【TraningApp】パスワード再設定のご案内");

        // 本文にトークン付きのURLを含める
        String resetUrl = baseUrl + "/password/reset?token=" + token;

        message.setText("以下のリンクをクリックしてパスワードの再設定を完了してください。\n"
                + "有効期限は30分です。\n\n"
                + resetUrl);

        mailSender.send(message);
    }
}
