package com.example.traning.organization;

/**
 * 招待コードの発行を、入力内容を理由に断る場合の例外（itバグ-49）。メッセージはそのまま画面のエラー表示に使う。
 *
 * <p>権限不足等は従来どおり{@link org.springframework.web.server.ResponseStatusException}で扱い、こちらは使わない。
 */
public class InviteCodeIssueRejectedException extends RuntimeException {

  public InviteCodeIssueRejectedException(String message) {
    super(message);
  }
}
