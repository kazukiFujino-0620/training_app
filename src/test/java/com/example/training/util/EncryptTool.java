package com.example.training.util;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

public class EncryptTool {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java EncryptTool <plaintext-to-encrypt>");
            System.err.println("Environment variable JASYPT_ENCRYPTOR_PASSWORD must be set");
            System.exit(1);
        }

        String masterPassword = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (masterPassword == null || masterPassword.isEmpty()) {
            System.err.println("Error: JASYPT_ENCRYPTOR_PASSWORD environment variable not set");
            System.exit(1);
        }

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(masterPassword);
        encryptor.setAlgorithm("PBEWithHmacSHA512AndAES_256");
        encryptor.setKeyObtentionIterations(1000);
        encryptor.setProviderName("SunJCE");

        String plaintext = args[0];
        String encrypted = encryptor.encrypt(plaintext);
        System.out.println("Encrypted: ENC(" + encrypted + ")");
    }
}