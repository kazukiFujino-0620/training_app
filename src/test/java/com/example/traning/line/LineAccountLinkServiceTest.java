package com.example.traning.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ita4-1 残作業: {@link LineAccountLinkService}のLINEアカウント後付け連携ロジックを検証する。
 * HttpClient・UserDaoはMockitoでモックし、実際のLINEサーバー・DBには接続しない。
 */
@ExtendWith(MockitoExtension.class)
class LineAccountLinkServiceTest {

  @Mock private HttpClient httpClient;
  @Mock private UserDao userDao;
  @Mock private HttpResponse<String> tokenResponse;
  @Mock private HttpResponse<String> profileResponse;

  private LineAccountLinkService service;

  @BeforeEach
  void setUp() {
    service = new LineAccountLinkService(httpClient, new ObjectMapper(), userDao);
    ReflectionTestUtils.setField(service, "clientId", "test-client-id");
    ReflectionTestUtils.setField(service, "clientSecret", "test-client-secret");
    ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
  }

  @Test
  void buildAuthorizeUrl_必要なパラメータを含む() {
    String url = service.buildAuthorizeUrl("state-123");

    assertThat(url).startsWith("https://access.line.me/oauth2/v2.1/authorize?");
    assertThat(url).contains("client_id=test-client-id");
    assertThat(url).contains("state=state-123");
    assertThat(url)
        .contains(
            "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fuser%2Fnotifications%2Fline%2Fcallback");
    assertThat(url).contains("scope=profile");
  }

  @Test
  void completeLink_未連携のLINEアカウントならlineIdが更新される() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"test-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body())
        .thenReturn("{\"userId\":\"U-new-line-id\",\"displayName\":\"テスト太郎\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    when(userDao.selectByLineId("U-new-line-id")).thenReturn(Optional.empty());

    service.completeLink(8, "auth-code");

    verify(userDao).updateLineId(8, "U-new-line-id");
  }

  @Test
  void completeLink_既に自分自身に連携済みなら再連携できる() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"test-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"userId\":\"U-existing\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    User self = User.builder().userId(8).build();
    when(userDao.selectByLineId("U-existing")).thenReturn(Optional.of(self));

    service.completeLink(8, "auth-code");

    verify(userDao).updateLineId(8, "U-existing");
  }

  @Test
  void completeLink_既に別ユーザーに連携済みのLINEアカウントは例外() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"test-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"userId\":\"U-taken\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    User otherUser = User.builder().userId(99).build();
    when(userDao.selectByLineId("U-taken")).thenReturn(Optional.of(otherUser));

    assertThatThrownBy(() -> service.completeLink(8, "auth-code"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(userDao, org.mockito.Mockito.never()).updateLineId(any(), any());
  }

  @Test
  void completeLink_トークン取得に失敗した場合は例外() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(400);
    when(tokenResponse.body()).thenReturn("{\"error\":\"invalid_grant\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse);

    assertThatThrownBy(() -> service.completeLink(8, "bad-code"))
        .isInstanceOf(IllegalStateException.class);
    verify(userDao, org.mockito.Mockito.never()).updateLineId(any(), any());
  }

  @Test
  void completeLink_トークン交換リクエストはフォームエンコードでPOSTする() throws Exception {
    when(tokenResponse.statusCode()).thenReturn(200);
    when(tokenResponse.body()).thenReturn("{\"access_token\":\"test-access-token\"}");
    when(profileResponse.statusCode()).thenReturn(200);
    when(profileResponse.body()).thenReturn("{\"userId\":\"U-new-line-id\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(tokenResponse, profileResponse);
    when(userDao.selectByLineId("U-new-line-id")).thenReturn(Optional.empty());

    service.completeLink(8, "auth-code");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient, org.mockito.Mockito.times(2))
        .send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest tokenRequest = captor.getAllValues().get(0);
    assertThat(tokenRequest.uri().toString()).isEqualTo("https://api.line.me/oauth2/v2.1/token");
    assertThat(tokenRequest.headers().firstValue("Content-Type"))
        .contains("application/x-www-form-urlencoded");

    HttpRequest profileRequest = captor.getAllValues().get(1);
    assertThat(profileRequest.uri().toString()).isEqualTo("https://api.line.me/v2/profile");
    assertThat(profileRequest.headers().firstValue("Authorization"))
        .contains("Bearer test-access-token");
  }
}
