package com.example.traning.smarttrainer.recommendation;

import java.util.List;

/**
 * その日の推奨メニュー。
 * `restDayRecommended` が true の場合、`partCode`/`items` は無意味（休養日提案）。
 */
public record DailyRecommendation(
    String partCode,
    String partLabel,
    String reasonLabel,
    List<RecommendedItem> items,
    boolean restDayRecommended) {

  public static DailyRecommendation restDay() {
    return new DailyRecommendation(null, null, "今日は軽めの有酸素や休養日を推奨", List.of(), true);
  }
}
