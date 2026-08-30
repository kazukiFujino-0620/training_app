package com.example.traning.mobile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.mfa.MfaService;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.mobile.dto.TokenResponse;
import com.example.traning.mobile.entity.MobileRefreshToken;
import com.example.traning.user.User;
import com.example.traning.user.service.LoginAttemptService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * モバイルアプリのGoogle/LINEログインで使う{@link MobileAuthService#loginViaOAuth}を検証する。
 * パスワード検証を経由しないOAuthログイン専用の経路のため、既存の{@code login()}系とは別ファイルで扱う。
 */
@ExtendWith(MockitoExtension.class)
class MobileAuthServiceOAuthTest {

  @Mock private JwtService jwtService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private UserDao userDao;
  @Mock private MobileRefreshTokenDao refreshTokenDao;
  @Mock private MfaService mfaService;
  @Mock private LoginAttemptService loginAttemptService;

  private MobileAuthService service;

  @BeforeEach
  void setUp() {
    service =
        new MobileAuthService(
            jwtService, passwordEncoder, userDao, refreshTokenDao, mfaService, loginAttemptService);
  }

  private User user(int id, String email, String role, boolean enabled) {
    return User.builder().userId(id).email(email).role(role).enabled(enabled).build();
  }

  @Test
  void loginViaOAuth_MFA無効ならアクセストークンを発行する() {
    User user = user(8, "taro@example.com", "ROLE_USER", true);
    when(mfaService.isEnabled(8L)).thenReturn(false);
    when(jwtService.generateAccessToken(8L, "taro@example.com", "ROLE_USER"))
        .thenReturn("access-token");
    when(passwordEncoder.encode(any())).thenReturn("hashed");
    when(jwtService.getRefreshTokenValidityMs()).thenReturn(7L * 24 * 60 * 60 * 1000);

    TokenResponse result = service.loginViaOAuth(user, "device-1");

    assertThat(result.isMfaRequired()).isFalse();
    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isNotBlank();
    verify(refreshTokenDao).insert(any(MobileRefreshToken.class));
  }

  @Test
  void loginViaOAuth_MFA有効なら仮トークンのみ返す() {
    User user = user(8, "taro@example.com", "ROLE_USER", true);
    when(mfaService.isEnabled(8L)).thenReturn(true);
    when(jwtService.generateMfaTempToken(8L, "device-1")).thenReturn("mfa-temp-token");

    TokenResponse result = service.loginViaOAuth(user, "device-1");

    assertThat(result.isMfaRequired()).isTrue();
    assertThat(result.getMfaTempToken()).isEqualTo("mfa-temp-token");
    verify(refreshTokenDao, never()).insert(any(MobileRefreshToken.class));
  }

  @Test
  void loginViaOAuth_無効化されたアカウントは例外() {
    User disabled = user(8, "taro@example.com", "ROLE_USER", false);

    assertThatThrownBy(() -> service.loginViaOAuth(disabled, "device-1"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(refreshTokenDao, never()).insert(any(MobileRefreshToken.class));
  }

  @Test
  void loginViaOAuth_退会済みアカウントは例外() {
    User withdrawn =
        User.builder()
            .userId(8)
            .email("taro@example.com")
            .role("ROLE_USER")
            .enabled(true)
            .deletedAt(LocalDateTime.now())
            .build();

    assertThatThrownBy(() -> service.loginViaOAuth(withdrawn, "device-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
