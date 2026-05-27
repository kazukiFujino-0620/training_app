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
        // メールヘッダーインジェクション防止: CR/LF を除去し、長さを検証する
        String sanitizedTo = sanitizeHeader(to);
        String sanitizedToken = sanitizeHeader(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(sanitizedTo);
        message.setSubject("【TraningApp】パスワード再設定のご案内");

        String resetUrl = baseUrl + "/password/reset?token=" + sanitizedToken;
        message.setText("以下のリンクをクリックしてパスワードの再設定を完了してください。\n"
                + "有効期限は30分です。\n\n"
                + resetUrl);

        mailSender.send(message);
    }

    public void sendRestoreMail(String to, String token) {
        String sanitizedTo = sanitizeHeader(to);
        String sanitizedToken = sanitizeHeader(token);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(sanitizedTo);
        message.setSubject("【TraningApp】アカウント復元のご案内");

        String restoreUrl = baseUrl + "/account/restore?token=" + sanitizedToken;
        message.setText("以下のリンクをクリックしてアカウントの復元を完了してください。\n"
                + "有効期限は24時間です。\n\n"
                + restoreUrl);

        mailSender.send(message);
    }

    /** ヘッダーインジェクションに使われる CR・LF・NUL を除去する */
    private static String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\x00]", "");
    }
}
