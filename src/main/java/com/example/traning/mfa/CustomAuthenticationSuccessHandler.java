package com.example.traning.mfa;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.service.CustomUserDetails;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserDao userDao;
    private final MfaService mfaService;

    public CustomAuthenticationSuccessHandler(UserDao userDao, MfaService mfaService) {
        this.userDao = userDao;
        this.mfaService = mfaService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
            Authentication authentication) throws IOException, ServletException {

        Object principal = authentication.getPrincipal();

        // OAuth2ユーザー（Google/LINE）は2FA不要
        if (principal instanceof CustomUserDetails) {
            res.sendRedirect("/menu");
            return;
        }

        String email = ((UserDetails) principal).getUsername();
        User user = userDao.selectByEmail(email).orElse(null);
        if (user == null) {
            log.warn("User not found after authentication: email hash={}", email.hashCode());
            res.sendRedirect("/menu");
            return;
        }

        Long userId = user.getUserId().longValue();

        if (mfaService.isEnabled(userId)) {
            HttpSession session = req.getSession(true);
            session.setAttribute("MFA_PENDING_USER_ID", userId);
            log.debug("2FA required for userId={}, redirecting to /auth/mfa", userId);
            res.sendRedirect("/auth/mfa");
        } else {
            res.sendRedirect("/menu");
        }
    }
}
