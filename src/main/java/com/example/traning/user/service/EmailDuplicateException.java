package com.example.traning.user.service;

/** メールアドレス重複による登録・変更失敗。メッセージは呼び出し元ごとに異なる文言を渡す。 */
public class EmailDuplicateException extends RuntimeException {

  public EmailDuplicateException(String message) {
    super(message);
  }
}
