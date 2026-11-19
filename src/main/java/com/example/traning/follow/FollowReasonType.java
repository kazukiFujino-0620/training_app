package com.example.traning.follow;

/** 「フォロー推奨」の印の理由の種類（機能見直し-1-#12）。「対応済み」の記録に押した時点の種類を残す。 */
public enum FollowReasonType {
  /** 最後の記録から14日以上。 */
  NO_RECENT_RECORD,
  /** 記録が1件も無く、担当になってから（不明・未割当は登録から）14日以上。 */
  NO_RECORDS,
  /** #7の週の目標の達成が2週続けて半分未満。 */
  LOW_ACHIEVEMENT,
  /** #4で理由を書いて種目を除外した（追加から14日間）。 */
  PAIN_REPORT
}
