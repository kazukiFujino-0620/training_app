package com.example.traning.mobile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * モバイルアプリのGoogle/LINEログイン用{@link MobileOAuthLoginService}を検証する。
 * HttpClient・UserDaoはMockitoでモックし、実際の外部サーバー・DBには接続しない。
 */
@ExtendWith(MockitoExtension.class)
class MobileOAuthLoginServiceTest {

  @Mock private HttpClient httpClient;
  @Mock private UserDao userDao;
  @Mock private HttpResponse<String> tokenResponse;
  @Mock private HttpResponse<String> profileResponse;

  private MobileOAuthLoginService service;

  @BeforeEach
  void setUp() {
    service = new MobileOAuthLoginService(httpClient, new ObjectMapper(), userDao);
    ReflectionTestUtils.setField(service, "googleClientId", "google-client-id");
    ReflectionTestUtils.setField(service, "googleClientSecret", "google-client-secret");
    ReflectionTestUtils.setField(service, "lineClientId", "line-client-id");
    ReflectionTestUtils.setField(service, "lineClientSecret", "line-client-secret");
    ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
  }

  @Test
  void buildAuthorizeUrl_google_必要なパラメータを含む() {
    String url = service.buildAuthorizeUrl("google", "state-123");

    assertThat(url).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
    assertThat(url).contains("client_id=google-client-id");
    assertThat(url).contains("state=state-123");
    assertThat(url)
        .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fmobile-oauth%2Fgoogle%2Fcallback");
    assertThat(url).contains("scope=profile+email");
  }

  @Test
  void buildAuthorizeUrl_line_必要なパラメータを含む() {
    String url = service.buildAuthorizeUrl("line", "state-456");

    assertThat(url).startsWith("https://access.line.me/oauth2/v2.1/authorize?");
    assertThat(url).contains("client_id=line-client-id");
    assertThat(url)
        .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fmobile-oauth%2Fline%2Fcallback");
  }

  @Test
  void buildAuthorizeUrl_未対応プロバイダーは例外() {
    assertThatThrownBy(() -> service.buildAuthorizeUrl("twitter", "state"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveUser_google_既存ユーザーをメールで検索して返す() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"g-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"email\":\"taro@example.com\",\"sub\":\"g-sub-1\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    User existing = User.builder().userId(8).email("taro@example.com").build();
    when(userDao.selectByEmail("taro@example.com")).thenReturn(Optional.of(existing));

    User result = service.resolveUser("google", "auth-code");

    assertThat(result).isEqualTo(existing);
  }

  @Test
  void resolveUser_google_未登録メールは例外() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"g-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"email\":\"unknown@example.com\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    when(userDao.selectByEmail("unknown@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolveUser("google", "auth-code"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveUser_line_lineIdで既存ユーザーを返す() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"l-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"userId\":\"U-existing\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    User existing = User.builder().userId(8).lineId("U-existing").build();
    when(userDao.selectByLineId("U-existing")).thenReturn(Optional.of(existing));

    User result = service.resolveUser("line", "auth-code");

    assertThat(result).isEqualTo(existing);
  }

  @Test
  void resolveUser_line_未連携のlineIdは例外() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"l-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"userId\":\"U-unknown\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    when(userDao.selectByLineId("U-unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolveUser("line", "auth-code"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveUser_トークン取得に失敗した場合は例外() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(400);
    when(tokenResponse.body()).thenReturn("{\"error\":\"invalid_grant\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse);

    assertThatThrownBy(() -> service.resolveUser("google", "bad-code"))
        .isInstanceOf(IllegalStateException.class);
  }
}
