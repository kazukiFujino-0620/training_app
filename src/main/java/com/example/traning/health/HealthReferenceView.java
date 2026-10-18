package com.example.traning.health;

import java.time.LocalDate;

/**
 * Web版メニュー画面の「体調の参考情報」カード用の表示データ（機能見直し-1-#5）。モバイルのHealthSummaryResponseとは別DTO。
 *
 * <p>各項目は記録が無ければnull。平均は直近4週間（前日まで）の記録から算出し、記録が無ければnull。
 * 表示文言はモバイル（expo-app/src/health/referenceFormat.ts）と揃えている。値の良し悪しは判定しない。
 */
public record HealthReferenceView(Sleep sleep, RestingHeartRate restingHeartRate) {

  /** 平均が計算できないとき（直近4週間に記録が無い）の文言。USER承認済み（QA 2026-10-15）。 */
  public static final String NO_AVERAGE_TEXT = "直近4週間の記録がまだないため、平均は表示できません";

  public record Sleep(
      LocalDate date,
      int durationMinutes,
      String source,
      Integer averageMinutes,
      int averageDays) {

    public String dateLabel() {
      return shortDate(date);
    }

    public String durationLabel() {
      return formatSleepMinutes(durationMinutes);
    }

    public String averageLabel() {
      return averageMinutes == null ? NO_AVERAGE_TEXT : "4週間平均 " + formatSleepMinutes(averageMinutes);
    }

    /** 平均が無いときはnull（差の行は出さない）。 */
    public String diffLabel() {
      if (averageMinutes == null) {
        return null;
      }
      int diff = durationMinutes - averageMinutes;
      if (diff == 0) {
        return "平均と同じ";
      }
      return diff < 0
          ? "平均より" + formatDiffMinutes(-diff) + "短い"
          : "平均より" + formatDiffMinutes(diff) + "長い";
    }
  }

  public record RestingHeartRate(
      LocalDate date, int restingBpm, String source, Integer averageBpm, int averageDays) {

    public String dateLabel() {
      return shortDate(date);
    }

    public String averageLabel() {
      return averageBpm == null ? NO_AVERAGE_TEXT : "4週間平均 " + averageBpm + " bpm";
    }

    /** 平均が無いときはnull（差の行は出さない）。 */
    public String diffLabel() {
      if (averageBpm == null) {
        return null;
      }
      int diff = restingBpm - averageBpm;
      if (diff == 0) {
        return "平均と同じ";
      }
      return diff < 0 ? "平均より" + (-diff) + " bpm低い" : "平均より" + diff + " bpm高い";
    }
  }

  public boolean isEmpty() {
    return sleep == null && restingHeartRate == null;
  }

  static String formatSleepMinutes(int minutes) {
    return (minutes / 60) + "時間" + (minutes % 60) + "分";
  }

  static String formatDiffMinutes(int minutes) {
    int h = minutes / 60;
    int m = minutes % 60;
    if (h == 0) {
      return m + "分";
    }
    return m == 0 ? h + "時間" : h + "時間" + m + "分";
  }

  static String shortDate(LocalDate d) {
    return d.getMonthValue() + "/" + d.getDayOfMonth();
  }
}
