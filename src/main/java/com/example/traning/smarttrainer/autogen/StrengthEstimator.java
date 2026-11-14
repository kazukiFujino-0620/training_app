package com.example.traning.smarttrainer.autogen;

import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 種目ごとの直近の記録から、重量の案に使う値を求める（機能見直し-1-#4 詳細設計書5-4節、QA Q4-12）。DBに触れない純粋な計算のみ。
 *
 * <p>生涯ベスト（personal_records）は使わない。休止明けや軽めの時期に重く出るため。
 */
public final class StrengthEstimator {

  private StrengthEstimator() {}

  /** 完了済みの本番セット1件（重量0より大きいもの）。 */
  public record SessionSet(LocalDate date, double weight, int reps) {}

  /**
   * @param oneRm 直近3セッションそれぞれの推定1RM最大値の平均。回数1〜12回のセットが無ければnull
   * @param lastMainWeight 直近セッションの最も重い本番セットの重量（推定1RMが無いときに使う。従来と同じ）
   * @param daysSinceLast 最後にやってからの日数（対象日基準）
   * @param lastSessionMaxWeight 直近セッションの最大作業重量
   * @param lastSessionReps 直近セッションで最大重量を挙げたセットの回数（同じ重量が複数あれば多い方）
   */
  public record Strength(
      Double oneRm,
      Double lastMainWeight,
      Integer daysSinceLast,
      Double lastSessionMaxWeight,
      Integer lastSessionReps) {

    public static final Strength NONE = new Strength(null, null, null, null, null);
  }

  /**
   * @param sets その種目の完了済み本番セット（直近のセッション分。日付の古いものが混ざっていてもよい）
   * @param date 生成の対象日
   */
  public static Strength estimate(
      List<SessionSet> sets, LocalDate date, OneRmPredictionService oneRm) {
    if (sets == null || sets.isEmpty()) return Strength.NONE;

    // 日付ごと（1日=1セッション）に、新しい順
    TreeMap<LocalDate, List<SessionSet>> byDate = new TreeMap<>(Comparator.reverseOrder());
    for (SessionSet s : sets) {
      if (s.date() == null || s.date().isAfter(date) || s.weight() <= 0) continue;
      byDate.computeIfAbsent(s.date(), d -> new ArrayList<>()).add(s);
    }
    if (byDate.isEmpty()) return Strength.NONE;

    double sum = 0;
    int sessions = 0;
    int counted = 0;
    for (Map.Entry<LocalDate, List<SessionSet>> e : byDate.entrySet()) {
      if (counted++ >= AutogenRules.ONE_RM_SESSIONS) break;
      double best =
          e.getValue().stream()
              .filter(s -> s.reps() >= 1 && s.reps() <= AutogenRules.ONE_RM_MAX_REPS)
              .mapToDouble(s -> oneRm.estimateOneRm(s.weight(), s.reps()))
              .max()
              .orElse(Double.NaN);
      if (!Double.isNaN(best)) {
        sum += best;
        sessions++;
      }
    }

    Map.Entry<LocalDate, List<SessionSet>> latest = byDate.firstEntry();
    SessionSet heaviest =
        latest.getValue().stream()
            .max(Comparator.comparingDouble(SessionSet::weight).thenComparingInt(SessionSet::reps))
            .orElseThrow();
    return new Strength(
        sessions > 0 ? sum / sessions : null,
        heaviest.weight(),
        (int) ChronoUnit.DAYS.between(latest.getKey(), date),
        heaviest.weight(),
        heaviest.reps());
  }
}
