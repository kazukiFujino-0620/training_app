package com.example.traning.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    message.setText("以下のリンクをクリックしてパスワードの再設定を完了してください。\n" + "有効期限は30分です。\n\n" + resetUrl);

    mailSender.send(message);
  }

  public void sendRestoreMail(String to, String token) {
    String sanitizedTo = sanitizeHeader(to);
    String sanitizedToken = sanitizeHeader(token);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(sanitizedTo);
    message.setSubject("【TraningApp】アカウント復元のご案内");

    String restoreUrl = baseUrl + "/account/restore?token=" + sanitizedToken;
    message.setText("以下のリンクをクリックしてアカウントの復元を完了してください。\n" + "有効期限は24時間です。\n\n" + restoreUrl);

    mailSender.send(message);
  }

  public void sendWithdrawalRequestedMail(String to, String userName, LocalDateTime requestedAt) {
    String sanitizedTo = sanitizeHeader(to);
    String sanitizedName = sanitizeHeader(userName);
    String formattedAt = requestedAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(sanitizedTo);
    message.setSubject("【TraningApp】退会申請を受け付けました");
    message.setText(
        sanitizedName
            + " 様\n\n"
            + "退会申請を受け付けました。\n"
            + "申請日時: "
            + formattedAt
            + "\n\n"
            + "管理者が処理するまで通常通りご利用いただけます。\n"
            + "申請をキャンセルする場合は、ログイン後にプロフィール設定の「退会申請」からお手続きいただけます。\n\n"
            + "【TraningApp】");
    mailSender.send(message);
  }

  public void sendWithdrawalCompletedMail(String to, String userName, LocalDateTime completedAt) {
    String sanitizedTo = sanitizeHeader(to);
    String sanitizedName = sanitizeHeader(userName);
    String formattedAt = completedAt.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(sanitizedTo);
    message.setSubject("【TraningApp】退会が完了しました");
    message.setText(
        sanitizedName
            + " 様\n\n"
            + "退会手続きが完了しました。\n"
            + "完了日時: "
            + formattedAt
            + "\n\n"
            + "お客様の全てのトレーニングデータを削除いたしました。\n"
            + "これまでのご利用ありがとうございました。\n\n"
            + "再登録はいつでも可能です。\n\n"
            + "【TraningApp】");
    mailSender.send(message);
  }

  public void sendEmailChangeMail(String newEmail, String token) {
    String sanitizedTo = sanitizeHeader(newEmail);
    String sanitizedToken = sanitizeHeader(token);

    String link = baseUrl + "/user/email/confirm?token=" + sanitizedToken;
    String body =
        "メールアドレス変更のご確認\n\n"
            + "以下のリンクをクリックして変更を確定してください（24時間有効）:\n"
            + link
            + "\n\n"
            + "このメールに心当たりがない場合は無視してください。\n\n"
            + "【TraningApp】";

    SimpleMailMessage msg = new SimpleMailMessage();
    msg.setTo(sanitizedTo);
    msg.setSubject("【TraningApp】メールアドレス変更の確認");
    msg.setText(body);
    mailSender.send(msg);
  }

  /** ヘッダーインジェクションに使われる CR・LF・NUL を除去する */
  private static String sanitizeHeader(String value) {
    if (value == null) {
      return "";
    }
    return value.replaceAll("[\\r\\n\\x00]", "");
  }
}
