package com.example.traning.line;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ita4-1（週・月トレーニング通知方法対応）: {@link LineMessagingService}のFlex Message送信ロジックの検証。
 * HttpClientはMockitoでモックし、実際のLINEサーバーには接続しない。
 */
@ExtendWith(MockitoExtension.class)
class LineMessagingServiceTest {

  @Mock private HttpClient httpClient;
  @Mock private HttpResponse<String> httpResponse;

  private LineMessagingService service;

  @BeforeEach
  void setUp() {
    service = new LineMessagingService(httpClient, new ObjectMapper());
    ReflectionTestUtils.setField(service, "channelAccessToken", "test-channel-access-token");
    ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080");
  }

  @Test
  void isConfigured_トークン未設定ならfalse() {
    ReflectionTestUtils.setField(service, "channelAccessToken", "");
    assertThat(service.isConfigured()).isFalse();
  }

  @Test
  void isConfigured_トークン設定済みならtrue() {
    assertThat(service.isConfigured()).isTrue();
  }

  @Test
  void sendWeeklySummary_トークン未設定時はHTTP送信をスキップする() throws Exception {
    ReflectionTestUtils.setField(service, "channelAccessToken", "");

    service.sendWeeklySummary(
        "U123",
        "テスト太郎",
        LocalDate.of(2026, 8, 10),
        LocalDate.of(2026, 8, 16),
        3,
        4200.0,
        Map.of("CHEST", 1500.0),
        12.0);

    verify(httpClient, never()).send(any(), any());
  }

  @Test
  void sendWeeklySummary_トークン設定時はPush_APIへPOSTする() throws Exception {
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    Map<String, Double> partVolumes = new LinkedHashMap<>();
    partVolumes.put("CHEST", 1500.0);
    partVolumes.put("BACK", 1800.0);

    service.sendWeeklySummary(
        "U123",
        "テスト太郎",
        LocalDate.of(2026, 8, 10),
        LocalDate.of(2026, 8, 16),
        3,
        4200.0,
        partVolumes,
        12.0);

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));

    HttpRequest sent = captor.getValue();
    assertThat(sent.uri().toString()).isEqualTo("https://api.line.me/v2/bot/message/push");
    assertThat(sent.headers().firstValue("Authorization"))
        .contains("Bearer test-channel-access-token");
  }

  @Test
  void sendWeeklySummary_ステータス異常時は例外を投げる() throws Exception {
    when(httpResponse.statusCode()).thenReturn(400);
    when(httpResponse.body()).thenReturn("{\"message\":\"invalid\"}");
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            service.sendWeeklySummary(
                "U123",
                "テスト太郎",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 16),
                3,
                4200.0,
                Map.of(),
                null));
  }

  @Test
  void sendWeeklySummary_送信IOエラー時は例外を投げる() throws Exception {
    when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            service.sendWeeklySummary(
                "U123",
                "テスト太郎",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 16),
                3,
                4200.0,
                Map.of(),
                null));
  }

  @Test
  void sendMonthlySummary_トークン未設定時はHTTP送信をスキップする() throws Exception {
    ReflectionTestUtils.setField(service, "channelAccessToken", "");

    service.sendMonthlySummary(
        "U123",
        "テスト太郎",
        2026,
        8,
        12,
        48500.0,
        Map.of("CHEST", 3),
        Map.of("CHEST", 12000.0),
        8.0,
        java.util.List.of());

    verify(httpClient, never()).send(any(), any());
  }
}
