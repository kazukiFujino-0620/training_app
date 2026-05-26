package com.example.traning.user.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security の認証イベントを購読し、ログイン試行を {@link LoginAttemptService} へ連携する。
 *
 * 効果:
 * - 連続失敗で当該ユーザー名（email）がロック対象になる
 * - 成功時にカウンタがリセットされる
 *
 * 監査ログ目的での出力も兼ねる（個人情報を直接出さず hashCode のみ）。
 */
@Component
@Slf4j
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginSucceeded(username);
        log.info("Authentication success: userHash={}",
                Integer.toHexString(username == null ? 0 : username.hashCode()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginFailed(username);
        log.warn("Authentication failure: userHash={}, cause={}",
                Integer.toHexString(username == null ? 0 : username.hashCode()),
                event.getException().getClass().getSimpleName());
    }
}
