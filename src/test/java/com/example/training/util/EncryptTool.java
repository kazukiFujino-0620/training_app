package com.example.training.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

public class EncryptTool {
    public static void main(String[] args) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        // 1. マスターパスワードを設定
        encryptor.setPassword("training-2026-app");

        // 2. 暗号化したいパスワード（GCP用）を入れる
        String result = encryptor.encrypt("ここにGCPの実際のパスワードを入れる");

        System.out.println("ENC(" + result + ")");
    }
}