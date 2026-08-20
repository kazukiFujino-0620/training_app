package com.example.traning.line;

import com.example.traning.common.SummaryMailService.GoalAchievementResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * LINE Messaging API（Push Message）で週次・月次サマリーをFlex Messageとして送信する（ita4-1）。
 *
 * <p>チャネルアクセストークン未設定時（LINE公式アカウント未開設の間）は送信せずログのみ出力し、例外を投げない。
 * WeeklySummaryTask/MonthlySummaryTaskの既存の「ユーザー単位try/catchでスキップ」運用と整合させるため、
 * 送信失敗（HTTPエラー等）は例外を投げてスキップさせる。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineMessagingService {

  private static final String PUSH_API_URL = "https://api.line.me/v2/bot/message/push";
  private static final List<String> PART_ORDER = List.of("CHEST", "BACK", "ARM", "SHOULDER", "LEG");

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Value("${line.messaging.channel-access-token:}")
  private String channelAccessToken;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  public boolean isConfigured() {
    return channelAccessToken != null && !channelAccessToken.isBlank();
  }

  public void sendWeeklySummary(
      String lineUserId,
      String userName,
      LocalDate weekStart,
      LocalDate weekEnd,
      int sessionCount,
      double totalVolume,
      Map<String, Double> partVolumes,
      Double volumeChangePercent) {

    if (!isConfigured()) {
      log.info("LINEチャネルアクセストークン未設定のため週次サマリー送信をスキップします");
      return;
    }

    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    String altText =
        String.format(
            "先週（%s〜%s）のトレーニングサマリーです", weekStart.format(fmt), weekEnd.format(fmt));

    List<String> bodyLines = new java.util.ArrayList<>();
    bodyLines.add(userName + " 様");
    bodyLines.add("先週（" + weekStart.format(fmt) + "〜" + weekEnd.format(fmt) + "）のサマリーです。");
    bodyLines.add("");
    bodyLines.add("📊 トレーニング回数: " + sessionCount + " 回");
    bodyLines.add("💪 総ボリューム: " + formatVolume(totalVolume) + " kg（" + buildVolumeChangeLine(volumeChangePercent) + "）");
    bodyLines.add("");
    bodyLines.add("🎯 部位別ボリューム");
    bodyLines.addAll(buildPartVolumeLines(partVolumes));

    push(lineUserId, altText, buildBubble("先週のトレーニングサマリー", bodyLines));
  }

  public void sendMonthlySummary(
      String lineUserId,
      String userName,
      int year,
      int month,
      int sessionCount,
      double totalVolume,
      Map<String, Integer> partSessionCounts,
      Map<String, Double> partVolumes,
      Double volumeChangePercent,
      List<GoalAchievementResult> goalResults) {

    if (!isConfigured()) {
      log.info("LINEチャネルアクセストークン未設定のため月次サマリー送信をスキップします");
      return;
    }

    String altText = String.format("先月（%d年%02d月）のトレーニングサマリーです", year, month);

    List<String> bodyLines = new java.util.ArrayList<>();
    bodyLines.add(userName + " 様");
    bodyLines.add("先月（" + year + "年" + String.format("%02d", month) + "月）のサマリーです。");
    bodyLines.add("");
    bodyLines.add("📊 トレーニング回数: " + sessionCount + " 回");
    bodyLines.add(
        "💪 総ボリューム: " + formatVolume(totalVolume) + " kg（" + buildVolumeChangeLine(volumeChangePercent) + "）");
    bodyLines.add("");
    bodyLines.add("🎯 部位別（回数・ボリューム）");
    bodyLines.addAll(buildPartSummaryLines(partSessionCounts, partVolumes));
    bodyLines.add("");
    bodyLines.add("🏆 目標達成状況");
    bodyLines.addAll(buildGoalLines(goalResults));

    push(lineUserId, altText, buildBubble("先月のトレーニングサマリー", bodyLines));
  }

  // ── Flex Message構築 ─────────────────────────────────────────────

  private ObjectNode buildBubble(String title, List<String> bodyLines) {
    ObjectNode bubble = objectMapper.createObjectNode();
    bubble.put("type", "bubble");

    ObjectNode header = objectMapper.createObjectNode();
    header.put("type", "box");
    header.put("layout", "vertical");
    ArrayNode headerContents = objectMapper.createArrayNode();
    ObjectNode titleText = objectMapper.createObjectNode();
    titleText.put("type", "text");
    titleText.put("text", title);
    titleText.put("weight", "bold");
    titleText.put("size", "md");
    headerContents.add(titleText);
    header.set("contents", headerContents);
    bubble.set("header", header);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("type", "box");
    body.put("layout", "vertical");
    ArrayNode bodyContents = objectMapper.createArrayNode();
    for (String line : bodyLines) {
      ObjectNode text = objectMapper.createObjectNode();
      text.put("type", "text");
      text.put("text", line.isEmpty() ? " " : line);
      text.put("wrap", true);
      text.put("size", "sm");
      bodyContents.add(text);
    }
    body.set("contents", bodyContents);
    bubble.set("body", body);

    ObjectNode footer = objectMapper.createObjectNode();
    footer.put("type", "box");
    footer.put("layout", "vertical");
    ArrayNode footerContents = objectMapper.createArrayNode();
    ObjectNode button = objectMapper.createObjectNode();
    button.put("type", "button");
    button.put("style", "primary");
    ObjectNode action = objectMapper.createObjectNode();
    action.put("type", "uri");
    action.put("label", "Smart Trainer");
    action.put("uri", baseUrl + "/menu");
    button.set("action", action);
    footerContents.add(button);
    footer.set("contents", footerContents);
    bubble.set("footer", footer);

    return bubble;
  }

  private void push(String to, String altText, ObjectNode flexContents) {
    try {
      ObjectNode message = objectMapper.createObjectNode();
      message.put("type", "flex");
      message.put("altText", altText);
      message.set("contents", flexContents);

      ArrayNode messages = objectMapper.createArrayNode();
      messages.add(message);

      ObjectNode requestBody = objectMapper.createObjectNode();
      requestBody.put("to", to);
      requestBody.set("messages", messages);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(PUSH_API_URL))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + channelAccessToken)
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
              .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "LINEメッセージ送信に失敗しました: status=" + response.statusCode() + " body=" + response.body());
      }
    } catch (IOException e) {
      throw new IllegalStateException("LINEメッセージ送信に失敗しました", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("LINEメッセージ送信が中断されました", e);
    }
  }

  // ── ヘルパー（SummaryMailServiceと同じ表記ロジック） ──────────────────

  private String buildVolumeChangeLine(Double changePercent) {
    if (changePercent == null) return "前回データなし";
    long pct = Math.round(changePercent);
    return "前回比 " + (pct >= 0 ? "+" : "") + pct + "%";
  }

  private List<String> buildPartVolumeLines(Map<String, Double> partVolumes) {
    List<String> lines =
        PART_ORDER.stream()
            .filter(partVolumes::containsKey)
            .map(c -> resolvePartLabel(c) + ": " + formatVolume(partVolumes.get(c)) + "kg")
            .toList();
    return lines.isEmpty() ? List.of("実施なし") : lines;
  }

  private List<String> buildPartSummaryLines(
      Map<String, Integer> partSessionCounts, Map<String, Double> partVolumes) {
    List<String> lines =
        PART_ORDER.stream()
            .filter(c -> partSessionCounts.containsKey(c) || partVolumes.containsKey(c))
            .map(
                c -> {
                  int count = partSessionCounts.getOrDefault(c, 0);
                  double volume = partVolumes.getOrDefault(c, 0.0);
                  return resolvePartLabel(c) + ": " + count + "回 / " + formatVolume(volume) + "kg";
                })
            .toList();
    return lines.isEmpty() ? List.of("実施なし") : lines;
  }

  private List<String> buildGoalLines(List<GoalAchievementResult> results) {
    if (results.isEmpty()) return List.of("目標未設定");
    return results.stream()
        .map(
            r -> {
              if (r.achieved()) {
                return "✓ " + r.itemName() + " " + r.targetWeight() + "kg: 達成";
              }
              String maxPart =
                  r.maxWeightInPeriod() != null ? "（最高 " + r.maxWeightInPeriod() + "kg）" : "";
              return "✗ " + r.itemName() + " " + r.targetWeight() + "kg: 未達" + maxPart;
            })
        .toList();
  }

  private String formatVolume(double volume) {
    return String.format("%,.0f", volume);
  }

  private String resolvePartLabel(String partCode) {
    return switch (partCode) {
      case "CHEST" -> "胸";
      case "BACK" -> "背中";
      case "ARM" -> "腕";
      case "SHOULDER" -> "肩";
      case "LEG" -> "脚";
      default -> partCode;
    };
  }
}
