package com.example.traning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Data
public class RateLimitProperties {

    /** POST /login: IP単位、1分あたりの上限回数 */
    private int loginMaxPerMinute = 10;

    /** POST /signup: IP単位、1時間あたりの上限回数 */
    private int signupMaxPerHour = 5;

    /** POST/PUT/DELETE /api/**: userId単位、1分あたりの上限回数 */
    private int apiWriteMaxPerMinute = 60;

    /** GET /api/**: userId単位、1分あたりの上限回数 */
    private int apiReadMaxPerMinute = 300;
}
