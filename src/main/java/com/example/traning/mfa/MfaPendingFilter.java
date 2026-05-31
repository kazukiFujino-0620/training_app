package com.example.traning.mfa;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * フォームログイン後に2FA未完了ユーザーを /auth/mfa へリダイレクトするフィルター。
 * セッションの MFA_PENDING_USER_ID が存在する間はMFA検証画面以外へのアクセスをブロックする。
 */
@Component
@Slf4j
public class MfaPendingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain) throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session == null) {
            chain.doFilter(req, res);
            return;
        }

        Long pendingUserId = (Long) session.getAttribute("MFA_PENDING_USER_ID");
        if (pendingUserId == null) {
            chain.doFilter(req, res);
            return;
        }

        String path = req.getRequestURI();
        if (isAllowedWhilePending(path)) {
            chain.doFilter(req, res);
            return;
        }

        log.debug("MFA pending for userId={}, blocking path={}", pendingUserId, path);
        res.sendRedirect("/auth/mfa");
    }

    private boolean isAllowedWhilePending(String path) {
        return path.startsWith("/auth/mfa")
                || path.startsWith("/logout")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/");
    }
}
