package com.example.traning.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jasypt 暗号化設定。
 */
@Configuration
public class JasyptConfig {

    /**
     * bootstrap.yml 経由で環境変数 JASYPT_ENCRYPTOR_PASSWORD を注入する。
     * 環境変数が未設定の場合は起動時に明示的に失敗させ、
     * デフォルト値を持たせないことで本番環境での設定漏れを防ぐ。
     */
    @Value("${jasypt.encryptor.password}")
    private String encryptorPassword;

    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        // ── アルゴリズム設定 ──────────────────────────────────────────
        // bootstrap.yml の設定と完全に一致させる
        config.setPassword(encryptorPassword);
        config.setAlgorithm("PBEWithHmacSHA512AndAES_256");
        // OWASP 推奨: PBKDF2-SHA512 で 210,000 回以上
        config.setKeyObtentionIterations("310000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");

        // ── IV 生成器 ─────────────────────────────────────────────────
        // AES_256 使用時は RandomIvGenerator が必須。
        // NoIvGenerator は DES 等のブロック暗号向けであり AES では不可。
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");

        // ── 出力形式 ──────────────────────────────────────────────────
        config.setStringOutputType("base64");

        encryptor.setConfig(config);
        return encryptor;
    }
}