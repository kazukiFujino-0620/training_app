package com.example.traning.periodization;

/** サイクル終了時にユーザーが選ぶ次の行動（QA Q3-7、設計書3-1節 renewCycle）。 */
public enum RenewChoice {
  /** 直近サイクルと同じ内容で継続する。 */
  REPEAT_SAME,
  /** 別のプリセットを選んで開始する。 */
  CHOOSE_NEW_PRESET,
  /** 期分けをやめて通常の週間プログラム（weekly_programs）運用に戻る。新しいサイクルは作らない。 */
  GO_FREEFORM
}
