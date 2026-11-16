package com.example.traning.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.traning.dao.UserDao;
import com.example.traning.filter.JwtAuthenticationFilter;
import com.example.traning.filter.RateLimitFilter;
import com.example.traning.mfa.CustomAuthenticationSuccessHandler;
import com.example.traning.mfa.MfaPendingFilter;
import com.example.traning.mfa.MfaService;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.mobile.service.JwtService;
import com.example.traning.user.User;
import com.example.traning.user.service.CustomOAuth2UserService;
import com.example.traning.user.service.UserSessionInvalidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * itバグ-48: 役割を変更したユーザーのログイン状態が、実際の {@link SecurityConfig}
 * のフィルターチェーン上で無効になることを検証する（Webのセッション・Remember-me・モバイルのアクセストークン）。
 *
 * <p>ユーザー情報は {@link InMemoryUserDetailsManager} で持ち、役割の変更（DBの更新）はその書き換えで代用する。
 * 管理画面のコントローラーの代わりに、URLごとの認可だけを確かめる仮のエンドポイントを置く。
 */
@SpringJUnitWebConfig(RoleChangeSessionInvalidationTest.TestConfig.class)
@TestPropertySource(
    properties = {
      "app.security.remember-me-key=test-remember-me-key",
      "app.jwt.secret=" + RoleChangeSessionInvalidationTest.JWT_SECRET
    })
class RoleChangeSessionInvalidationTest {

  static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  private static final String PASSWORD = "password";
  private static final String GYM_ADMIN = "gym-admin@example.com";
  private static final String TARGET = "store-admin@example.com";
  private static final int TARGET_ID = 20;

  @Autowired private WebApplicationContext context;
  @Autowired private InMemoryUserDetailsManager users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private UserSessionInvalidator invalidator;
  @Autowired private MobileRefreshTokenDao mobileRefreshTokenDao;
  @Autowired private UserDao userDao;
  @Autowired private JwtService jwtService;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    reset(mobileRefreshTokenDao, userDao);
    putUser(GYM_ADMIN, "ORG_ADMIN");
    putUser(TARGET, "STORE_ADMIN");
  }

  @Test
  void 役割を変更すると対象ユーザーの既存セッションは次のリクエストでログイン画面に戻る() throws Exception {
    MockHttpSession target = login(TARGET, false).session;
    mvc.perform(get("/admin/users").session(target)).andExpect(status().isOk());

    changeRole(TARGET, TARGET_ID, "TRAINER");

    mvc.perform(get("/admin/users").session(target)).andExpect(redirectedUrl("/login"));
    // 期限切れ後はログアウト済み（未認証）として扱われる
    mvc.perform(get("/admin/audit-logs").session(target)).andExpect(redirectedUrl("/login"));
    verify(mobileRefreshTokenDao).deleteByUserId((long) TARGET_ID);
  }

  @Test
  void 役割を変更しても操作した管理者のセッションは維持される() throws Exception {
    MockHttpSession admin = login(GYM_ADMIN, false).session;
    MockHttpSession target = login(TARGET, false).session;

    changeRole(TARGET, TARGET_ID, "TRAINER");

    mvc.perform(get("/admin/users").session(admin)).andExpect(status().isOk());
    mvc.perform(get("/admin/users").session(target)).andExpect(redirectedUrl("/login"));
  }

  @Test
  void 自分自身の役割を変更した場合は自分のセッションも無効になる() throws Exception {
    MockHttpSession admin = login(GYM_ADMIN, false).session;

    changeRole(GYM_ADMIN, 10, "STORE_ADMIN");

    mvc.perform(get("/admin/users").session(admin)).andExpect(redirectedUrl("/login"));
  }

  @Test
  void 期限切れのセッションへのAJAXリクエストは401を返す() throws Exception {
    MockHttpSession target = login(TARGET, false).session;

    changeRole(TARGET, TARGET_ID, "TRAINER");

    mvc.perform(get("/admin/users").session(target).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void RememberMeクッキーは期限切れ時に削除され_残ったクッキーでも変更後の役割しか得られない() throws Exception {
    LoginResult login = login(TARGET, true);
    Cookie rememberMe = login.rememberMe;
    assertThat(rememberMe).isNotNull();

    changeRole(TARGET, TARGET_ID, "TRAINER");

    MvcResult expired =
        mvc.perform(get("/admin/users").session(login.session).cookie(rememberMe))
            .andExpect(redirectedUrl("/login"))
            .andReturn();
    Cookie cleared = expired.getResponse().getCookie("remember-me-cookie");
    assertThat(cleared).isNotNull();
    assertThat(cleared.getMaxAge()).isZero();

    // 別端末などに残ったクッキー（セッションなし）で自動ログインしても、権限はDBから読み直される
    MvcResult autoLogin =
        mvc.perform(get("/admin/all-users-training").cookie(rememberMe))
            .andExpect(status().isOk())
            .andReturn();
    SecurityContext securityContext =
        (SecurityContext)
            autoLogin
                .getRequest()
                .getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertThat(securityContext.getAuthentication().getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_TRAINER");
    // 変更前の役割（店舗管理者）の画面には入れない（Remember-meでの拒否はSpring Securityの仕様でログイン画面へ戻す）
    mvc.perform(get("/admin/users").cookie(rememberMe)).andExpect(redirectedUrl("/login"));
  }

  @Test
  void モバイルのアクセストークンは役割の変更前に発行されたものを拒否する() throws Exception {
    String token = jwtService.generateAccessToken((long) TARGET_ID, TARGET, "ROLE_STORE_ADMIN");

    when(userDao.selectById(TARGET_ID)).thenReturn(entity(TARGET_ID, TARGET, "ROLE_STORE_ADMIN"));
    mvc.perform(get("/api/mobile/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    when(userDao.selectById(TARGET_ID)).thenReturn(entity(TARGET_ID, TARGET, "ROLE_TRAINER"));
    mvc.perform(get("/api/mobile/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());

    // 変更後の役割で発行し直したトークンは使える
    String reissued = jwtService.generateAccessToken((long) TARGET_ID, TARGET, "ROLE_TRAINER");
    mvc.perform(get("/api/mobile/ping").header("Authorization", "Bearer " + reissued))
        .andExpect(status().isOk());
  }

  @Test
  void モバイルのアクセストークンはユーザーが存在しない場合も拒否する() throws Exception {
    String token = jwtService.generateAccessToken((long) TARGET_ID, TARGET, "ROLE_USER");
    when(userDao.selectById(TARGET_ID)).thenReturn(null);

    mvc.perform(get("/api/mobile/ping").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  // ── ヘルパー ────────────────────────────────────────────────────────

  private record LoginResult(MockHttpSession session, Cookie rememberMe) {}

  private LoginResult login(String email, boolean rememberMe) throws Exception {
    var request = post("/login").param("username", email).param("password", PASSWORD).with(csrf());
    if (rememberMe) {
      request.param("remember-me", "on");
    }
    MvcResult result = mvc.perform(request).andExpect(redirectedUrl("/menu")).andReturn();
    return new LoginResult(
        (MockHttpSession) result.getRequest().getSession(false),
        result.getResponse().getCookie("remember-me-cookie"));
  }

  /** DBの役割を書き換え、実際の更新処理（UserService#updateUserInfo）と同じく無効化を呼ぶ。 */
  private void changeRole(String email, int userId, String role) {
    putUser(email, role);
    invalidator.invalidateAll(entity(userId, email, "ROLE_" + role));
  }

  private void putUser(String email, String role) {
    // Remember-meの署名はパスワードを含むため、登録済みならパスワードは変えずに役割だけを差し替える
    boolean exists = users.userExists(email);
    String password =
        exists ? users.loadUserByUsername(email).getPassword() : passwordEncoder.encode(PASSWORD);
    var details =
        org.springframework.security.core.userdetails.User.withUsername(email)
            .password(password)
            .roles(role)
            .build();
    if (exists) {
      users.updateUser(details);
    } else {
      users.createUser(details);
    }
  }

  private static User entity(int userId, String email, String role) {
    return User.builder().userId(userId).email(email).role(role).build();
  }

  // ── テスト用の設定 ──────────────────────────────────────────────────

  @Configuration
  @EnableWebMvc
  @Import({
    SecurityConfig.class,
    JwtService.class,
    JwtAuthenticationFilter.class,
    MfaPendingFilter.class,
    CustomAuthenticationSuccessHandler.class,
    UserSessionInvalidator.class,
    TestEndpoints.class
  })
  static class TestConfig {

    @Bean
    InMemoryUserDetailsManager userDetailsService() {
      return new InMemoryUserDetailsManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    @Bean
    UserDao userDao() {
      return mock(UserDao.class);
    }

    @Bean
    MobileRefreshTokenDao mobileRefreshTokenDao() {
      return mock(MobileRefreshTokenDao.class);
    }

    @Bean
    MfaService mfaService() {
      return mock(MfaService.class);
    }

    @Bean
    CustomOAuth2UserService customOAuth2UserService() {
      return mock(CustomOAuth2UserService.class);
    }

    /** レート制限はこのテストの対象外のため、素通しにする。 */
    @Bean
    RateLimitFilter rateLimitFilter() throws Exception {
      RateLimitFilter filter = mock(RateLimitFilter.class);
      doAnswer(
              inv -> {
                inv.<FilterChain>getArgument(2)
                    .doFilter(
                        inv.<ServletRequest>getArgument(0), inv.<ServletResponse>getArgument(1));
                return null;
              })
          .when(filter)
          .doFilter(any(), any(), any());
      return filter;
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          CommonOAuth2Provider.GOOGLE
              .getBuilder("google")
              .clientId("test")
              .clientSecret("test")
              .build());
    }
  }

  @RestController
  static class TestEndpoints {

    @GetMapping({"/admin/users", "/admin/audit-logs", "/admin/all-users-training", "/menu"})
    String ok() {
      return "ok";
    }

    @GetMapping("/api/mobile/ping")
    String ping() {
      return "pong";
    }
  }
}
