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
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String email = oAuth2User.getAttribute("email");
        String sub = oAuth2User.getAttribute("sub"); // Googleの固有ID
        String name = oAuth2User.getAttribute("name"); // Googleに登録されている名前

        Optional<User> userOpt = userDao.selectByEmail(email);
        User user;

        if (userOpt.isEmpty()) {
            // --- 新規登録の処理 ---
            SignupForm signupForm = new SignupForm();
            signupForm.setEmail(email);
            signupForm.setUsername(name);
            signupForm.setPassword("");
            signupForm.setGoogleId(sub);

            signupTransaction.execute(signupForm);

            user = userDao.selectByEmail(email)
                    .orElseThrow(() -> new OAuth2AuthenticationException("登録に失敗しました"));
        } else {
            if (Boolean.compare(userOpt.get().getEnabled(), false) == 0) {
                String msg = "このメールアドレスは退会済みです: " + email +
                        " 再度登録する場合は、管理者に連絡ください。";

                log.error(msg);
                throw new OAuth2AuthenticationException(msg);
            }

            // 既に登録済みの場合は、そのままログイン
            user = userOpt.get();
        }
        return new CustomUserDetails(oAuth2User, user.getUserId(), user.getRole());
    }
}