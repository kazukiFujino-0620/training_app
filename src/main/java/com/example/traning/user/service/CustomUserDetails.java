package com.example.traning.user.service;

import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class CustomUserDetails extends DefaultOAuth2User {
    private final Integer userId; // DBの主キー

    public CustomUserDetails(OAuth2User oAuth2User, Integer userId) {
        // 第3引数の "email" は、Googleの属性の中で識別子として使うキーです
        super(oAuth2User.getAuthorities(), oAuth2User.getAttributes(), "email");
        this.userId = userId;
    }

    public Integer getUserId() {
        return userId;
    }
}
