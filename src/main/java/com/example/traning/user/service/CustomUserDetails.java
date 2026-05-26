package com.example.traning.user.service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomUserDetails implements OAuth2User, UserDetails {
    private final OAuth2User oAuth2User;
    private final Integer userId; // DBの主キー
    private final String email;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(OAuth2User oAuth2User, Integer userId, String role, String email) {
        this.oAuth2User = oAuth2User;
        this.userId = userId;
        this.email = email;

        // ロールからROLE_プレフィックスを削除して権限を設定
        String roleName = role.replace("ROLE_", "");
        this.authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleName));

        log.debug("CustomUserDetails created - userId: {}, role: {}", userId, role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oAuth2User.getAttributes();
    }

    @Override
    public String getPassword() {
        return null; // OAuth2ユーザーはパスワードを持たない
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return oAuth2User.getAttribute("name");
    }

    public Integer getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }
}
