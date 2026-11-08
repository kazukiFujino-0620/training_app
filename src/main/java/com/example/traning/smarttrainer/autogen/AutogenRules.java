package com.example.traning.smarttrainer.autogen;

import com.example.traning.smarttrainer.recommendation.GoalMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * メニュー自動作成（機能見直し-1-#4）の数値をまとめた定数。詳細設計書5章（QA Q4-7・Q4-8）。
 *
 * <p>値の出どころはtraining-coordinator（AI）の提案で、文献確認は経ていない暫定値。運用で見直すときはこのクラスだけ変更する。
 */
public final class AutogenRules {

  private AutogenRules() {}

  // ── 入力 ──
  /** 使える時間の下限（分）。最低構成（1種目×2セット）が必ず入る長さ。 */
  public static final int MIN_AVAILABLE_MINUTES = 15;

  public static final int MAX_AVAILABLE_MINUTES = 240;

  // ── 所要時間の見積もり ──
  public static final int SECONDS_PER_REP = 4;
  public static final int PREP_SECONDS_COMPOUND = 120;
  public static final int PREP_SECONDS_ISOLATION = 60;

  /** 全身ウォームアップ。使える時間がしきい値未満なら短い方。 */
  public static final int WARMUP_SECONDS = 300;

  public static final int WARMUP_SECONDS_SHORT = 180;
  public static final int WARMUP_SHORT_THRESHOLD_MINUTES = 20;

  /** 最初の複合種目のウォームアップセット枠（記録用セットとしては生成しない）。 */
  public static final int FIRST_COMPOUND_WARMUP_SET_SECONDS = 180;

  // ── 上限・下限 ──
  public static final int MAX_SETS_PER_SESSION = 24;
  public static final int MAX_SETS_PER_PART = 10;
  public static final int MAX_ITEMS = 6;
  public static final int MIN_SETS_PER_ITEM = 2;

  // ── 部位 ──
  /** 大きい部位（重み2＝2種目）。この順で並べる。 */
  public static final List<String> BIG_PARTS = List.of("LEG", "BACK", "CHEST");

  /** 小さい部位（重み1＝1種目）。この順で並べる。 */
  public static final List<String> SMALL_PARTS = List.of("SHOULDER", "ARM");

  /** 体幹（重み1、並びは最後）。部位マスタに追加された場合に効く。 */
  public static final Set<String> CORE_PARTS = Set.of("ABS");

  /** 生成の対象にしない部位。 */
  public static final Set<String> NON_TARGET_PARTS = Set.of("CARDIO");

  public static final int BIG_PART_ITEMS = 2;
  public static final int SMALL_PART_ITEMS = 1;

  // ── 疲労 ──
  /** これを超えると高疲労（筋肉疲労マップで一番濃い色の帯）。 */
  public static final int HIGH_FATIGUE_THRESHOLD_PCT = 65;

  /** おまかせで全部位が高疲労のとき、各種目から減らすセット数。 */
  public static final int ALL_FATIGUED_SET_REDUCTION = 1;

  // ── 強度と回数（Q4-8） ──
  public record IntensityReps(double intensity, int reps) {}

  public static final Map<GoalMode, IntensityReps> GOAL_INTENSITY_REPS =
      Map.of(
          GoalMode.BULKING, new IntensityReps(0.70, 10),
          GoalMode.CUTTING, new IntensityReps(0.60, 12),
          GoalMode.MAINTENANCE, new IntensityReps(0.70, 8));

  /** プログラムの強度%を使うとき、その%での最大回数から引く回数。 */
  public static final int PROGRAM_REPS_MARGIN = 2;

  // ── 重量 ──
  /** 器具の刻みが分からない種目の刻み(kg)。 */
  public static final double DEFAULT_WEIGHT_INCREMENT_KG = 2.5;

  public static final String BODYWEIGHT_CATEGORY = "BODYWEIGHT";

  // ── 除外の再確認（Q4-4） ──
  public static final int EXCLUSION_FIRST_CONFIRM_DAYS = 14;
  public static final int EXCLUSION_REPEAT_CONFIRM_DAYS = 28;
  public static final int EXCLUSION_REASON_MAX_LENGTH = 200;

  // ── その他 ──
  public static final int MAX_ALTERNATIVES = 5;

  /** 種目の選び方に使う、よく行っている種目の集計期間（日）。 */
  public static final int RECENT_ITEM_WINDOW_DAYS = 30;

  /** 部位ごとの種目数（重み）。 */
  public static int itemsForPart(String partCode) {
    return BIG_PARTS.contains(partCode) ? BIG_PART_ITEMS : SMALL_PART_ITEMS;
  }

  /** 大きい部位→小さい部位→その他→体幹の並び順。 */
  public static int partSizeRank(String partCode) {
    int big = BIG_PARTS.indexOf(partCode);
    if (big >= 0) return big;
    int small = SMALL_PARTS.indexOf(partCode);
    if (small >= 0) return BIG_PARTS.size() + small;
    if (CORE_PARTS.contains(partCode)) return 1000;
    return 100;
  }
}
