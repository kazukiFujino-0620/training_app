package com.example.traning.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserDao userDao;

    public CustomUserDetailsService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // メールアドレスは個人情報のため DEBUG レベルでのみ出力する
        log.debug("Form login attempt for user");
        User user = userDao.selectByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません"));

        if (Boolean.FALSE.equals(user.getEnabled())) {
            // 退会済みの旨をセッションに保存し、ログには userId のみ記録する
            log.warn("Login attempt for disabled account - userId: {}", user.getUserId());
            saveLoginErrorReasonToSession("withdrawn");
            throw new UsernameNotFoundException("このアカウントは無効です");
        }

        // OAuth2ユーザーはパスワードがないためフォームログイン不可
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            log.warn("Form login attempt for OAuth2-only account - userId: {}", user.getUserId());
            saveLoginErrorReasonToSession("oauth2_user");
            throw new UsernameNotFoundException("OAuth2専用アカウントです");
        }

        log.debug("User authenticated successfully - userId: {}, role: {}",
                user.getUserId(), user.getRole());
        String roleName = user.getRole().replace("ROLE_", "");

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(roleName)
                .build();
    }

    private void saveLoginErrorReasonToSession(String reason) {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                    (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpSession session = attributes.getRequest().getSession(true);
                session.setAttribute("LOGIN_ERROR_REASON", reason);
            }
        } catch (Exception e) {
            log.error("Failed to save login error reason to session", e);
        }
    }
}
