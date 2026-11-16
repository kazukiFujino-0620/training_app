package com.example.traning.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.user.User;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** itバグ-48: {@link UserSessionInvalidator} が対象ユーザーのセッションだけを、コミット後に期限切れにすることを検証する。 */
@ExtendWith(MockitoExtension.class)
class UserSessionInvalidatorTest {

  @Mock private MobileRefreshTokenDao mobileRefreshTokenDao;

  private SessionRegistryImpl registry;
  private UserSessionInvalidator invalidator;

  private final User target = User.builder().userId(20).email("staff@example.com").build();

  @BeforeEach
  void setUp() {
    registry = new SessionRegistryImpl();
    invalidator = new UserSessionInvalidator(registry, mobileRefreshTokenDao);
    registry.registerNewSession("target-form", formUser("staff@example.com"));
    registry.registerNewSession("target-oauth", oauthUser("Staff@Example.com"));
    registry.registerNewSession("other", formUser("admin@example.com"));
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void 対象ユーザーのセッションだけを期限切れにしてリフレッシュトークンを削除する() {
    invalidator.invalidateAll(target);

    assertThat(registry.getSessionInformation("target-form").isExpired()).isTrue();
    assertThat(registry.getSessionInformation("target-oauth").isExpired()).isTrue();
    assertThat(registry.getSessionInformation("other").isExpired()).isFalse();
    verify(mobileRefreshTokenDao).deleteByUserId(20L);
  }

  @Test
  void トランザクション内ではコミット後にセッションを期限切れにする() {
    TransactionSynchronizationManager.initSynchronization();

    invalidator.invalidateAll(target);
    assertThat(registry.getSessionInformation("target-form").isExpired()).isFalse();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(registry.getSessionInformation("target-form").isExpired()).isTrue();
  }

  private static UserDetails formUser(String email) {
    return org.springframework.security.core.userdetails.User.withUsername(email)
        .password("x")
        .roles("STORE_ADMIN")
        .build();
  }

  private static CustomUserDetails oauthUser(String email) {
    var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_STORE_ADMIN"));
    var oauth2User = new DefaultOAuth2User(authorities, Map.of("name", "staff"), "name");
    return new CustomUserDetails(oauth2User, 20, "ROLE_STORE_ADMIN", email);
  }
}
