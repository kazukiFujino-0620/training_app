package com.example.traning.user.service;

import java.util.Optional;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserDao userDao;
    private final SignupServiceTransaction signupTransaction;

    public CustomOAuth2UserService(UserDao userDao, SignupServiceTransaction signupTransaction) {
        this.userDao = userDao;
        this.signupTransaction = signupTransaction;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("OAuth2 login attempt started");
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("OAuth2 provider: {}", registrationId);

        String email;
        String providerId;
        String name;

        if ("google".equals(registrationId)) {
            // Google OAuth2
            email = oAuth2User.getAttribute("email");
            providerId = oAuth2User.getAttribute("sub"); // Googleの固有ID
            name = oAuth2User.getAttribute("name"); // Googleに登録されている名前
        } else if ("line".equals(registrationId)) {
            // LINE OAuth2
            providerId = oAuth2User.getAttribute("userId"); // LINEの固有ID
            name = oAuth2User.getAttribute("displayName"); // LINEの表示名
            if (name == null || name.isEmpty()) {
                name = "LINEユーザー";
            }
            // LINEではemailが取得できない場合があるため、フォールバック処理
            email = oAuth2User.getAttribute("email");
            if (email == null || email.isEmpty()) {
                // LINEのuserIdをベースにした一時的なメールアドレスを使用
                email = "line_" + providerId + "@temp.local";
                log.warn("LINE OAuth2 email not available, using temporary email: {}", email);
            }
        } else {
            throw new OAuth2AuthenticationException("未対応のOAuth2プロバイダー: " + registrationId);
        }

        log.info("OAuth2 user info - provider: {}, name: {}, providerId: {}", registrationId, name, providerId);

        Optional<User> userOpt = userDao.selectByEmail(email);
        User user;

        if (userOpt.isEmpty()) {
            // --- 新規登録の処理 ---
            log.info("New user registration via OAuth2 - provider: {}", registrationId);
            try {
                SignupForm signupForm = new SignupForm();
                signupForm.setEmail(email);
                signupForm.setUsername(name);
                // OAuth2経由の場合、仮パスワードが自動生成される
                signupForm.setPassword("");
                signupForm.setPassword_confirm("");

                if ("google".equals(registrationId)) {
                    signupForm.setGoogleId(providerId);
                } else if ("line".equals(registrationId)) {
                    signupForm.setLineId(providerId);
                }

                signupTransaction.execute(signupForm);
                log.info("OAuth2 user registration completed - provider: {}", registrationId);

                final String emailForLambda = email;
                user = userDao.selectByEmail(emailForLambda)
                        .orElseThrow(() -> new OAuth2AuthenticationException("登録後のユーザー検索に失敗しました: " + emailForLambda));
            } catch (Exception e) {
                log.error("OAuth2 user registration failed - provider: {}", registrationId, e);
                throw new OAuth2AuthenticationException("OAuth2ユーザー登録処理に失敗しました: " + e.getMessage());
            }
        } else {
            log.info("Existing user found via OAuth2 - enabled: {}", userOpt.get().getEnabled());
            if (Boolean.FALSE.equals(userOpt.get().getEnabled())) {
                String msg = "このアカウントは退会済みです。再度登録する場合は、管理者に連絡ください。";

                log.error(msg);
                throw new OAuth2AuthenticationException(msg);
            }

            // 既に登録済みの場合は、そのままログイン
            user = userOpt.get();
        }

        log.debug("Creating CustomUserDetails - userId: {}", user.getUserId());
        return new CustomUserDetails(oAuth2User, user.getUserId(), user.getRole(), user.getEmail());
    }
}