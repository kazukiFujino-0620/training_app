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

        log.info("OAuth2 user info - provider: {}, email: {}, name: {}, providerId: {}", registrationId, email, name,
                providerId);

        Optional<User> userOpt = userDao.selectByEmail(email);
        User user;

        if (userOpt.isEmpty()) {
            // --- 新規登録の処理 ---
            log.info("New user registration via OAuth2 - provider: {}, email: {}", registrationId, email);
            SignupForm signupForm = new SignupForm();
            signupForm.setEmail(email);
            signupForm.setUsername(name);
            signupForm.setPassword("");
            signupForm.setPassword_confirm("");

            if ("google".equals(registrationId)) {
                signupForm.setGoogleId(providerId);
            } else if ("line".equals(registrationId)) {
                signupForm.setLineId(providerId);
            }

            signupTransaction.execute(signupForm);

            user = userDao.selectByEmail(email)
                    .orElseThrow(() -> new OAuth2AuthenticationException("登録に失敗しました"));
        } else {
            log.info("Existing user found via OAuth2 - email: {}, enabled: {}", email, userOpt.get().getEnabled());
            if (Boolean.compare(userOpt.get().getEnabled(), false) == 0) {
                String msg = "このメールアドレスは退会済みです: " + email +
                        " 再度登録する場合は、管理者に連絡ください。";

                log.error(msg);
                throw new OAuth2AuthenticationException(msg);
            }

            // 既に登録済みの場合は、そのままログイン
            user = userOpt.get();
        }

        log.info("Creating CustomUserDetails - userId: {}, role: {}", user.getUserId(), user.getRole());
        return new CustomUserDetails(oAuth2User, user.getUserId(), user.getRole());
    }
}