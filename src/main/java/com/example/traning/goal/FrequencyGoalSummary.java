package com.example.traning.goal;

import java.util.ArrayList;
import java.util.List;

/**
 * 「週の目標」カードと設定シートの表示データ（機能見直し-1-#7）。Web・モバイル共通のService層の戻り値で、
 * 画面へはそれぞれ別DTOに詰め替えて渡す。表示文言はUSER承認済みの仮画面（パターンA〜G）に合わせる。
 *
 * <p>「あと◯回」「今週中に」等の急がせる文言・目標超過の加点・週ごとの○×一覧は出さない（設計書5-1節）。
 *
 * @param goalSet 今週の目標があるか。falseなら未設定の案内（パターンD）を出す
 * @param thisWeekTarget 今週の目標日数（週ごとの確定目標）。goalSet=falseのときnull
 * @param thisWeekDoneDays 今週（月曜〜今日）にトレーニングした日数
 * @param thisWeekFromProgram 今週の目標が期分けプログラムの予定日数か（ラベルに「プログラム」の印を付ける）
 * @param evaluatedWeeks 達成率の計算に使った終わった週の数（0〜4）
 * @param achievedWeeks そのうち目標を達成した週の数
 * @param manualTarget 手入力の目標（未設定ならnull）。設定シートの初期値
 * @param programActive 期分けプログラムの実施中か（設定シートを表示のみにする。パターンG）
 */
public record FrequencyGoalSummary(
    boolean goalSet,
    Integer thisWeekTarget,
    int thisWeekDoneDays,
    boolean thisWeekFromProgram,
    int evaluatedWeeks,
    int achievedWeeks,
    Integer manualTarget,
    boolean programActive) {

  /** 達成率を計算する週数（QA Q7-4）。 */
  public static final int EVALUATION_WEEKS = 4;

  /** 達成率（%）を出し始める終わった週の数（QA Q7-8。training-coordinatorの暫定値で、運用で見直し可）。 */
  public static final int PERCENT_MIN_WEEKS = 4;

  /** プログラムを始めた週の設定シートの説明文（2026-10-18 USER承認。翌週以降は仮画面Gの文言）。 */
  public static final String PROGRAM_STARTS_NEXT_WEEK_TEXT = "来週から、その週の予定日数を目標にします。";

  /** 達成率（%）。終わった週がPERCENT_MIN_WEEKS未満ならnull（分数だけ出す）。 */
  public Integer achievementPct() {
    if (!goalSet || evaluatedWeeks < PERCENT_MIN_WEEKS) return null;
    return (int) Math.round(achievedWeeks * 100.0 / evaluatedWeeks);
  }

  /** 例: 「週3回」。今週の目標が0日（プログラムの予定が無い週）のときはnull。 */
  public String goalLabel() {
    if (!goalSet || thisWeekTarget == null || thisWeekTarget == 0) return null;
    return "週" + thisWeekTarget + "回";
  }

  /** 例: 「今週 1/3回」（中立表示。残り回数は出さない）。 */
  public String thisWeekLabel() {
    if (!goalSet) return null;
    if (thisWeekTarget == null || thisWeekTarget == 0) return "今週 " + thisWeekDoneDays + "回";
    return "今週 " + thisWeekDoneDays + "/" + thisWeekTarget + "回";
  }

  /** 例: 「直近4週間 3/4週達成」「直近2週 1/2週達成」「来週から達成率を表示します」。 */
  public String resultLabel() {
    if (!goalSet) return null;
    if (evaluatedWeeks == 0) return "来週から達成率を表示します";
    if (evaluatedWeeks < PERCENT_MIN_WEEKS) {
      return "直近" + evaluatedWeeks + "週 " + achievedWeeks + "/" + evaluatedWeeks + "週達成";
    }
    return "直近" + evaluatedWeeks + "週間 " + achievedWeeks + "/" + evaluatedWeeks + "週達成";
  }

  /** 例: 「（75%）」。%を出さない段階ではnull。 */
  public String percentLabel() {
    Integer pct = achievementPct();
    return pct == null ? null : "（" + pct + "%）";
  }

  /** 結果の下に小さく出す補足（0〜2行）。 */
  public List<String> notes() {
    List<String> notes = new ArrayList<>();
    if (!goalSet) return notes;
    if (evaluatedWeeks > 0 && evaluatedWeeks < PERCENT_MIN_WEEKS) {
      notes.add("4週たつと達成率（%）も表示します");
    }
    if (thisWeekFromProgram) {
      notes.add("プログラムの予定日数を目標にしています");
    }
    return notes;
  }

  /** 設定シート（プログラム実施中、パターンG）の説明文。実施中でなければnull。 */
  public String programSheetLabel() {
    if (!programActive) return null;
    if (thisWeekFromProgram && thisWeekTarget != null && thisWeekTarget > 0) {
      return "プログラムの実施中は、その週の予定日数（今週は" + thisWeekTarget + "回）を目標にしています。";
    }
    // 週の途中でプログラムを始めた週は、今週の目標は始める前のまま（週の開始時点で確定。QA Q7-7）。
    // この週だけ文言を差し替える（2026-10-18 USER承認）
    return PROGRAM_STARTS_NEXT_WEEK_TEXT;
  }
}
