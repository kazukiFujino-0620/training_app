package com.example.traning.user.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.traning.filter.IpUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Security の認証イベントを購読し、ログイン試行を {@link LoginAttemptService} と
 * {@link IpBlockService} へ連携する。
 */
@Component
@Slf4j
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;
    private final IpBlockService ipBlockService;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService,
                                       IpBlockService ipBlockService) {
        this.loginAttemptService = loginAttemptService;
        this.ipBlockService = ipBlockService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginSucceeded(username);
        ipBlockService.recordSuccess(resolveIp());
        log.info("Authentication success: userHash={}",
                Integer.toHexString(username == null ? 0 : username.hashCode()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.loginFailed(username);
        ipBlockService.recordFailure(resolveIp());
        log.warn("Authentication failure: userHash={}, cause={}",
                Integer.toHexString(username == null ? 0 : username.hashCode()),
                event.getException().getClass().getSimpleName());
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = attrs.getRequest();
            return IpUtils.resolveClientIp(req);
        } catch (IllegalStateException e) {
            return "unknown";
        }
    }
}
