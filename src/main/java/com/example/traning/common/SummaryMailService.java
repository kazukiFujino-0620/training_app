package com.example.traning.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SummaryMailService {

  private final JavaMailSender mailSender;

  @Value("${app.base-url:http://localhost:8080}")
  private String baseUrl;

  public SummaryMailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void sendWeeklySummary(
      String to,
      String userName,
      LocalDate weekStart,
      LocalDate weekEnd,
      int sessionCount,
      double totalVolume,
      List<String> partsTrainedCodes,
      Double volumeChangePercent) {

    String sanitizedTo = sanitizeHeader(to);
    String sanitizedName = sanitizeHeader(userName);

    String partLabels =
        partsTrainedCodes.stream().map(this::resolvePartLabel).collect(Collectors.joining(" / "));
    if (partLabels.isEmpty()) partLabels = "なし";

    String volumeChangeLine = buildVolumeChangeLine(volumeChangePercent, "前々週");

    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    String subject =
        String.format(
            "【TraningApp】先週のトレーニングサマリー（%s〜%s）",
            weekStart.format(fmt), weekEnd.format(fmt));

    String body =
        sanitizedName
            + " 様\n\n"
            + "先週（"
            + weekStart.format(fmt)
            + "〜"
            + weekEnd.format(fmt)
            + "）のトレーニングサマリーです。\n\n"
            + "■ トレーニング回数: "
            + sessionCount
            + " 回\n"
            + "■ 総ボリューム: "
            + formatVolume(totalVolume)
            + " kg\n"
            + "■ 前々週比: "
            + volumeChangeLine
            + "\n"
            + "■ 実施部位: "
            + partLabels
            + "\n\n"
            + "引き続き頑張りましょう！\n\n"
            + "【TraningApp】";

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(sanitizedTo);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }

  public void sendMonthlySummary(
      String to,
      String userName,
      int year,
      int month,
      int sessionCount,
      double totalVolume,
      Map<String, Integer> partSessionCounts,
      Double volumeChangePercent,
      List<GoalAchievementResult> goalResults) {

    String sanitizedTo = sanitizeHeader(to);
    String sanitizedName = sanitizeHeader(userName);

    String volumeChangeLine = buildVolumeChangeLine(volumeChangePercent, "前月");
    String partSummary = buildPartSummaryLine(partSessionCounts);
    String goalLines = buildGoalLines(goalResults);

    String subject = String.format("【TraningApp】先月のトレーニングサマリー（%d年%02d月）", year, month);

    String body =
        sanitizedName
            + " 様\n\n"
            + "先月（"
            + year
            + "年"
            + String.format("%02d", month)
            + "月）のトレーニングサマリーです。\n\n"
            + "■ トレーニング回数: "
            + sessionCount
            + " 回\n"
            + "■ 総ボリューム: "
            + formatVolume(totalVolume)
            + " kg\n"
            + "■ 前月比: "
            + volumeChangeLine
            + "\n"
            + "■ 部位別: "
            + partSummary
            + "\n\n"
            + "■ 目標達成状況\n"
            + goalLines
            + "\n"
            + "【TraningApp】";

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(sanitizedTo);
    message.setSubject(subject);
    message.setText(body);
    mailSender.send(message);
  }

  // ── ヘルパー ─────────────────────────────────────────────────────────

  private String buildVolumeChangeLine(Double changePercent, String baseLabel) {
    if (changePercent == null) return baseLabel + "データなし";
    long pct = Math.round(changePercent);
    return (pct >= 0 ? "+" : "") + pct + "%";
  }

  private String buildPartSummaryLine(Map<String, Integer> partSessionCounts) {
    List<String> order = List.of("CHEST", "BACK", "ARM", "SHOULDER", "LEG");
    String result =
        order.stream()
            .filter(partSessionCounts::containsKey)
            .map(c -> resolvePartLabel(c) + " " + partSessionCounts.get(c) + "回")
            .collect(Collectors.joining(" / "));
    return result.isEmpty() ? "なし" : result;
  }

  private String buildGoalLines(List<GoalAchievementResult> results) {
    if (results.isEmpty()) return "  目標未設定\n";
    StringBuilder sb = new StringBuilder();
    for (GoalAchievementResult r : results) {
      if (r.achieved()) {
        sb.append("  ✓ ")
            .append(r.itemName())
            .append(" ")
            .append(r.targetWeight())
            .append("kg: 達成\n");
      } else {
        sb.append("  ✗ ")
            .append(r.itemName())
            .append(" ")
            .append(r.targetWeight())
            .append("kg: 未達");
        if (r.maxWeightInPeriod() != null) {
          sb.append("（最高 ").append(r.maxWeightInPeriod()).append("kg）");
        }
        sb.append("\n");
      }
    }
    return sb.toString();
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

  private static String sanitizeHeader(String value) {
    if (value == null) return "";
    return value.replaceAll("[\\r\\n\\x00]", "");
  }

  public record GoalAchievementResult(
      String itemName,
      BigDecimal targetWeight,
      BigDecimal maxWeightInPeriod,
      boolean achieved) {}
}
