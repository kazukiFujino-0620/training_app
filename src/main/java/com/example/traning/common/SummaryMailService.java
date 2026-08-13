package com.example.traning.common;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SummaryMailService {

  private static final Logger logger = LoggerFactory.getLogger(SummaryMailService.class);
  private static final List<String> PART_ORDER = List.of("CHEST", "BACK", "ARM", "SHOULDER", "LEG");

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
      Map<String, Double> partVolumes,
      Double volumeChangePercent) {

    String sanitizedName = escapeHtml(sanitizeHeader(userName));
    String volumeChangeLine = buildVolumeChangeLine(volumeChangePercent);
    String partVolumeRows = buildPartVolumeRows(partVolumes);

    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    String subject =
        String.format(
            "【TraningApp】先週のトレーニングサマリー（%s〜%s）", weekStart.format(fmt), weekEnd.format(fmt));

    String html =
        "<p>"
            + sanitizedName
            + " 様</p>"
            + "<p>先週（"
            + weekStart.format(fmt)
            + "〜"
            + weekEnd.format(fmt)
            + "）のトレーニングサマリーです。</p>"
            + "<p>📊 トレーニング回数: "
            + sessionCount
            + " 回<br>"
            + "💪 総ボリューム: "
            + formatVolume(totalVolume)
            + " kg（"
            + volumeChangeLine
            + "）</p>"
            + "<p>🎯 部位別ボリューム<br>"
            + partVolumeRows
            + "</p>"
            + buildSmartTrainerButton()
            + "<p>引き続き頑張りましょう！</p>"
            + "<p>【TraningApp】</p>";

    sendHtmlMail(to, subject, html);
  }

  public void sendMonthlySummary(
      String to,
      String userName,
      int year,
      int month,
      int sessionCount,
      double totalVolume,
      Map<String, Integer> partSessionCounts,
      Map<String, Double> partVolumes,
      Double volumeChangePercent,
      List<GoalAchievementResult> goalResults) {

    String sanitizedName = escapeHtml(sanitizeHeader(userName));
    String volumeChangeLine = buildVolumeChangeLine(volumeChangePercent);
    String partSummaryRows = buildPartSummaryRows(partSessionCounts, partVolumes);
    String goalRows = buildGoalRows(goalResults);

    String subject = String.format("【TraningApp】先月のトレーニングサマリー（%d年%02d月）", year, month);

    String html =
        "<p>"
            + sanitizedName
            + " 様</p>"
            + "<p>先月（"
            + year
            + "年"
            + String.format("%02d", month)
            + "月）のトレーニングサマリーです。</p>"
            + "<p>📊 トレーニング回数: "
            + sessionCount
            + " 回<br>"
            + "💪 総ボリューム: "
            + formatVolume(totalVolume)
            + " kg（"
            + volumeChangeLine
            + "）</p>"
            + "<p>🎯 部位別（セッション回数・ボリューム）<br>"
            + partSummaryRows
            + "</p>"
            + "<p>🏆 目標達成状況<br>"
            + goalRows
            + "</p>"
            + buildSmartTrainerButton()
            + "<p>今月も頑張りましょう！</p>"
            + "<p>【TraningApp】</p>";

    sendHtmlMail(to, subject, html);
  }

  // ── ヘルパー ─────────────────────────────────────────────────────────

  private void sendHtmlMail(String to, String subject, String html) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setTo(sanitizeHeader(to));
      helper.setSubject(sanitizeHeader(subject));
      helper.setText(html, true);
      mailSender.send(message);
    } catch (MessagingException e) {
      logger.error("HTMLメール送信に失敗しました: to={}", to, e);
      throw new IllegalStateException("メール送信に失敗しました", e);
    }
  }

  private String buildSmartTrainerButton() {
    return "<p><a href=\""
        + baseUrl
        + "/menu\" style=\"display:inline-block;padding:10px 24px;background-color:#4CAF50;"
        + "color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold;\">"
        + "Smart Trainer</a></p>";
  }

  private String buildVolumeChangeLine(Double changePercent) {
    if (changePercent == null) return "前回データなし";
    long pct = Math.round(changePercent);
    return "前回比 " + (pct >= 0 ? "+" : "") + pct + "%";
  }

  private String buildPartVolumeRows(Map<String, Double> partVolumes) {
    String rows =
        PART_ORDER.stream()
            .filter(partVolumes::containsKey)
            .map(c -> "　" + resolvePartLabel(c) + ": " + formatVolume(partVolumes.get(c)) + "kg")
            .collect(java.util.stream.Collectors.joining("<br>"));
    return rows.isEmpty() ? "　実施なし" : rows;
  }

  private String buildPartSummaryRows(
      Map<String, Integer> partSessionCounts, Map<String, Double> partVolumes) {
    String rows =
        PART_ORDER.stream()
            .filter(c -> partSessionCounts.containsKey(c) || partVolumes.containsKey(c))
            .map(
                c -> {
                  int count = partSessionCounts.getOrDefault(c, 0);
                  double volume = partVolumes.getOrDefault(c, 0.0);
                  return "　" + resolvePartLabel(c) + ": " + count + "回 / " + formatVolume(volume) + "kg";
                })
            .collect(java.util.stream.Collectors.joining("<br>"));
    return rows.isEmpty() ? "　実施なし" : rows;
  }

  private String buildGoalRows(List<GoalAchievementResult> results) {
    if (results.isEmpty()) return "　目標未設定";
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (GoalAchievementResult r : results) {
      if (!first) sb.append("<br>");
      first = false;
      String itemName = escapeHtml(r.itemName());
      if (r.achieved()) {
        sb.append("　✓ ").append(itemName).append(" ").append(r.targetWeight()).append("kg: 達成");
      } else {
        sb.append("　✗ ").append(itemName).append(" ").append(r.targetWeight()).append("kg: 未達");
        if (r.maxWeightInPeriod() != null) {
          sb.append("（最高 ").append(r.maxWeightInPeriod()).append("kg）");
        }
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

  /** メールヘッダー（To/Subject）へのヘッダーインジェクション対策。 */
  private static String sanitizeHeader(String value) {
    if (value == null) return "";
    return value.replaceAll("[\\r\\n\\x00]", "");
  }

  /** HTML本文に埋め込むユーザー入力由来の文字列をエスケープする（XSS対策）。 */
  private static String escapeHtml(String value) {
    if (value == null) return "";
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  public record GoalAchievementResult(
      String itemName, BigDecimal targetWeight, BigDecimal maxWeightInPeriod, boolean achieved) {}
}
