package com.example.traning.health;

/**
 * 睡眠・安静時心拍の手動入力の受付範囲（機能見直し-1-#5）。Web・モバイルの両方で共有する。
 *
 * <p>値はtraining-coordinatorの専門的判断による運用上の目安であり、公的な基準値ではない（根拠: 会議録 機能見直し-1_会議録 #5
 * の2026-10-10追記、USER承認: 機能見直し-1_QA 2026-10-11追記）。入力ミスを防ぐためのもので、値の良し悪しを
 * 判定するものではない。運用後に調整できるよう、境界値はすべてこのクラスの定数にまとめている。
 *
 * <ul>
 *   <li>入力エラー範囲（HARD）: 保存しない
 *   <li>確認範囲（HARDの内側でSOFTの外側）: 利用者が確認した場合（confirmed=true）のみ保存する
 *   <li>通常範囲（SOFT）: そのまま保存する
 * </ul>
 *
 * <p>自動同期（HealthKit/Health Connect）の値には確認を出さず、そのまま保存する。
 */
public final class HealthInputLimits {

  private HealthInputLimits() {}

  // ── 安静時心拍（bpm、整数） ──
  /** これ未満は入力エラー。4週間平均の計算からも除外する。 */
  public static final int RESTING_BPM_HARD_MIN = 25;

  /** これを超えると入力エラー。4週間平均の計算からも除外する。 */
  public static final int RESTING_BPM_HARD_MAX = 150;

  /** これ未満（HARD_MIN以上）は確認してから保存。 */
  public static final int RESTING_BPM_SOFT_MIN = 40;

  /** これを超える（HARD_MAX以下）と確認してから保存。 */
  public static final int RESTING_BPM_SOFT_MAX = 100;

  public static final String RESTING_BPM_ERROR_MESSAGE =
      "安静時心拍は" + RESTING_BPM_HARD_MIN + "〜" + RESTING_BPM_HARD_MAX + "bpmの範囲で入力してください";

  // ── 睡眠時間（分）。0（徹夜）は保存可能で、記録なしとは区別する ──
  public static final int SLEEP_MINUTES_HARD_MIN = 0;

  /** 24時間を超えると入力エラー。 */
  public static final int SLEEP_MINUTES_HARD_MAX = 24 * 60;

  /** 3時間未満は確認してから保存。 */
  public static final int SLEEP_MINUTES_SOFT_MIN = 3 * 60;

  /** 14時間を超えると確認してから保存。 */
  public static final int SLEEP_MINUTES_SOFT_MAX = 14 * 60;

  public static final String SLEEP_ERROR_MESSAGE = "睡眠時間は0〜24時間の範囲で入力してください";

  /** 確認範囲の値に出す確認文。値の評価（異常・受診等）はしない。 */
  public static final String CONFIRM_MESSAGE = "入力された値は、よく入力される範囲から外れています。入力内容に間違いがなければ、このまま保存できます";

  /** 入力の判定結果。 */
  public enum Check {
    OK,
    NEEDS_CONFIRMATION,
    ERROR
  }

  public static Check checkRestingBpm(int bpm) {
    if (bpm < RESTING_BPM_HARD_MIN || bpm > RESTING_BPM_HARD_MAX) {
      return Check.ERROR;
    }
    if (bpm < RESTING_BPM_SOFT_MIN || bpm > RESTING_BPM_SOFT_MAX) {
      return Check.NEEDS_CONFIRMATION;
    }
    return Check.OK;
  }

  public static Check checkSleepMinutes(int minutes) {
    if (minutes < SLEEP_MINUTES_HARD_MIN || minutes > SLEEP_MINUTES_HARD_MAX) {
      return Check.ERROR;
    }
    if (minutes < SLEEP_MINUTES_SOFT_MIN || minutes > SLEEP_MINUTES_SOFT_MAX) {
      return Check.NEEDS_CONFIRMATION;
    }
    return Check.OK;
  }

  /** 4週間平均の計算に含めてよい安静時心拍か（自動同期で入った入力エラー範囲の値を除外する）。 */
  public static boolean isRestingBpmUsableForAverage(int bpm) {
    return bpm >= RESTING_BPM_HARD_MIN && bpm <= RESTING_BPM_HARD_MAX;
  }
}
