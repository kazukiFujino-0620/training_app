package com.example.traning.periodization;

import java.util.ArrayList;
import java.util.List;

/**
 * 白紙から組むサイクル（createCustomCycle）の週数範囲と、保存を妨げない注意表示のルール（QA Q3-6見直し追記、2026-09-23 USER承認）。
 *
 * <p>閾値は運用後に調整できるよう定数化している。根拠は会議録#3の2026-09-23追記（training-coordinator回答）を参照。
 */
public final class CustomCycleRules {

  private CustomCycleRules() {}

  /** 設定できる最小週数。 */
  public static final int MIN_TOTAL_WEEKS = 2;

  /**
   * 設定できる最大週数。上限12週は生理学的な限界ではなく、1つにまとめて入力できるプログラムの最大長という設計上の上限
   * （training-coordinator回答、会議録#3の2026-09-23追記参照）。
   */
  public static final int MAX_TOTAL_WEEKS = 12;

  /** ディロード週を挟まない負荷週がこの週数以上連続したら注意表示する（保存は妨げない）。 */
  public static final int LONG_LOAD_STREAK_WARN_WEEKS = 7;

  /** この週数以上のサイクルにディロード週が1つも無ければ注意表示する（保存は妨げない）。 */
  public static final int NO_DELOAD_WARN_MIN_WEEKS = 5;

  /** 2週を選んだときの補足（入力は可能）。 */
  public static final String SHORT_CYCLE_NOTE = "2週サイクルはディロードの頻度が高くなりやすい点にご留意ください。";

  public static final String LONG_LOAD_STREAK_WARNING =
      "ディロード週を挟まない期間が長めです。疲労が溜まりやすいため、途中にディロード週を入れることも検討してください。";

  public static final String NO_DELOAD_WARNING =
      "ディロード週が設定されていません。疲労が溜まりやすいため、途中にディロード週を入れることも検討してください。";

  public enum Warning {
    SHORT_CYCLE(SHORT_CYCLE_NOTE),
    LONG_LOAD_STREAK(LONG_LOAD_STREAK_WARNING),
    NO_DELOAD(NO_DELOAD_WARNING);

    private final String message;

    Warning(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  /**
   * 週番号順のディロード週フラグから、表示すべき補足・注意を返す（いずれも保存は妨げない）。
   *
   * @param deloadByWeek 1週目から順のディロード週フラグ（要素数=週数）
   */
  public static List<Warning> evaluate(List<Boolean> deloadByWeek) {
    List<Warning> warnings = new ArrayList<>();
    int totalWeeks = deloadByWeek.size();
    if (totalWeeks == MIN_TOTAL_WEEKS) {
      warnings.add(Warning.SHORT_CYCLE);
    }
    int streak = 0;
    int maxStreak = 0;
    boolean anyDeload = false;
    for (Boolean deload : deloadByWeek) {
      if (Boolean.TRUE.equals(deload)) {
        anyDeload = true;
        streak = 0;
      } else {
        streak++;
        maxStreak = Math.max(maxStreak, streak);
      }
    }
    if (maxStreak >= LONG_LOAD_STREAK_WARN_WEEKS) {
      warnings.add(Warning.LONG_LOAD_STREAK);
    }
    if (totalWeeks >= NO_DELOAD_WARN_MIN_WEEKS && !anyDeload) {
      warnings.add(Warning.NO_DELOAD);
    }
    return warnings;
  }
}
