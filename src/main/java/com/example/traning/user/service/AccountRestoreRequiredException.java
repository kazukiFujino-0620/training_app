package com.example.traning.user.service;

public class AccountRestoreRequiredException extends RuntimeException {

    public AccountRestoreRequiredException() {
        super("このメールアドレスのアカウントは休止中です。復元メールを送信しました。");
    }
}
