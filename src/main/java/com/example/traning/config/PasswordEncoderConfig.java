package com.example.traning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * PasswordEncoder を SecurityConfig から分離した独立設定クラス。
 *
 * SecurityConfig → CustomOAuth2UserService → SignupServiceTransaction → PasswordEncoder
 * という依存チェーンが SecurityConfig 内に PasswordEncoder @Bean があると循環依存になる。
 * このクラスに切り出すことで循環を断ち切る。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
