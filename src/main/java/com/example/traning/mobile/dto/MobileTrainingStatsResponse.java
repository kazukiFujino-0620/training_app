package com.example.traning.mobile.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * カレンダータブ下の統計バー（ita7-2）。Web版 {@code /menu} の統計バーと同じ内容
 * （今月の回数・先週比ボリューム・今週の部位カバレッジ・今日の曜日別プログラム）をモバイル向けDTOで返す。
 * Web用APIとはController・DTOを分離している（CLAUDE.md方針）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileTrainingStatsResponse {

  /** 今月のトレーニング実施回数 */
  private int monthlyCount;

  /** 先週比の表示テキスト（例: "+12%"、データが無い場合は "前週データなし"） */
  private String volumeChangeText;

  /** 先週比がプラスかどうか（表示色の切り替え用。前週データなしの場合はtrue固定） */
  private boolean volumeChangePositive;

  /** 今週実施した部位のリスト（部位名＋実施済みかどうか） */
  private List<PartCoverage> weekParts;

  /** 今日の曜日別プログラムで設定された部位名。未設定の場合はnull */
  private String todayPartLabel;

  /** 当月のトレーニング実施日一覧（"yyyy-MM-dd"形式、重複無し）。カレンダータブの実施日ドット表示用（ita7-3）。 */
  private List<String> trainingDates;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PartCoverage {
    private String name;
    private boolean done;
  }
}
