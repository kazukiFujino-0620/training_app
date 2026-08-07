package com.example.traning.smarttrainer.recommendation;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * ルールベース推奨エンジン。 部位選定・種目選定は共通ロジック、重量/回数/セット数のみ {@link RecommendationStrategy} に委譲する。
 * [[2026-06-06-ai-menu-requirements]] 「推奨ロジック詳細」に準拠。
 */
@Service
public class RecommendationEngine {

  private static final Map<String, String> PART_LABEL =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  /** デフォルト重量（PRが無い種目向け）: 体重 × 0.5。体重が不明な場合は 20kg 固定。 */
  private static final double DEFAULT_WEIGHT_FACTOR = 0.5;

  private static final double DEFAULT_WEIGHT_FALLBACK = 20.0;

  public DailyRecommendation generate(
      LocalDate today,
      boolean isNewUser,
      Map<String, Integer> fatiguePct,
      Map<String, LocalDate> lastTrained,
      List<PersonalRecord> userPrs,
      Map<String, List<String>> recentItemNamesByPart,
      Map<String, List<TrainingItemMaster>> masterItemsByPart,
      RecommendationStrategy strategy,
      Double userWeightKg) {

    if (isNewUser) {
      String part = "CHEST";
      List<String> itemNames =
          masterFallbackItems(part, masterItemsByPart).stream()
              .map(TrainingItemMaster::getItemName)
              .toList();
      return buildForPart(part, "最初のメニューを記録してみよう", itemNames, userPrs, strategy, userWeightKg);
    }

    // priority 1: 5日以上未実施の部位（複数あれば最長期間優先）
    String targetPart = null;
    long maxDaysSince = -1;
    for (String p : FatigueCalculator.PART_ORDER) {
      LocalDate last = lastTrained.get(p);
      long daysSince = (last == null) ? Long.MAX_VALUE / 2 : ChronoUnit.DAYS.between(last, today);
      if (daysSince >= 5 && daysSince > maxDaysSince) {
        maxDaysSince = daysSince;
        targetPart = p;
      }
    }
    if (targetPart != null) {
      String reason =
          (maxDaysSince >= Long.MAX_VALUE / 4)
              ? "5日以上していない: " + PART_LABEL.get(targetPart)
              : "5日以上していない: " + PART_LABEL.get(targetPart) + "（最終実施: " + maxDaysSince + "日前）";
      return buildForPart(
          targetPart,
          reason,
          selectItems(targetPart, recentItemNamesByPart, masterItemsByPart),
          userPrs,
          strategy,
          userWeightKg);
    }

    // priority 2: 疲労度 30% 以下の部位（最低疲労順）
    String lowestFatiguePart = null;
    int lowestFatigue = Integer.MAX_VALUE;
    for (String p : FatigueCalculator.PART_ORDER) {
      int pct = fatiguePct.getOrDefault(p, 0);
      if (pct <= 30 && pct < lowestFatigue) {
        lowestFatigue = pct;
        lowestFatiguePart = p;
      }
    }
    if (lowestFatiguePart != null) {
      return buildForPart(
          lowestFatiguePart,
          "推奨部位: " + PART_LABEL.get(lowestFatiguePart),
          selectItems(lowestFatiguePart, recentItemNamesByPart, masterItemsByPart),
          userPrs,
          strategy,
          userWeightKg);
    }

    // priority 3: 全部位疲労中 → 休養日推奨
    return DailyRecommendation.restDay();
  }

  private List<TrainingItemMaster> masterFallbackItems(
      String part, Map<String, List<TrainingItemMaster>> masterItemsByPart) {
    List<TrainingItemMaster> items = masterItemsByPart.getOrDefault(part, List.of());
    return items.stream().limit(3).toList();
  }

  /** 種目選定: 過去30日の実施頻度 top3 → 履歴ゼロならマスタの display_order top3 */
  private List<String> selectItems(
      String part,
      Map<String, List<String>> recentItemNamesByPart,
      Map<String, List<TrainingItemMaster>> masterItemsByPart) {
    List<String> recent = recentItemNamesByPart.getOrDefault(part, List.of());
    if (!recent.isEmpty()) {
      return recent;
    }
    return masterFallbackItems(part, masterItemsByPart).stream()
        .map(TrainingItemMaster::getItemName)
        .toList();
  }

  private DailyRecommendation buildForPart(
      String part,
      String reasonLabel,
      List<String> itemNames,
      List<PersonalRecord> userPrs,
      RecommendationStrategy strategy,
      Double userWeightKg) {

    List<RecommendedItem> items = new ArrayList<>();
    for (String itemName : itemNames) {
      Optional<PersonalRecord> pr =
          userPrs.stream().filter(p -> p.getItemName().equals(itemName)).findFirst();
      double baseWeight =
          pr.map(PersonalRecord::getMaxWeight)
              .orElseGet(
                  () ->
                      userWeightKg != null
                          ? userWeightKg * DEFAULT_WEIGHT_FACTOR
                          : DEFAULT_WEIGHT_FALLBACK);
      double weightMin = round(baseWeight * strategy.getWeightPctMin());
      double weightMax = round(baseWeight * strategy.getWeightPctMax());
      items.add(
          new RecommendedItem(
              itemName,
              weightMin,
              weightMax,
              strategy.getRepsMin(),
              strategy.getRepsMax(),
              strategy.getSets()));
    }

    return new DailyRecommendation(part, PART_LABEL.get(part), reasonLabel, items, false);
  }

  private double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
