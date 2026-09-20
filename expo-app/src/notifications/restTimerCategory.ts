// 機能見直し-1-#6: 休憩タイマー通知への操作ボタン（延長/スキップ）
//
// 既存のバックグラウンド限定インターバル通知（PR#169, TrainingStartScreen.tsx）に
// スマートウォッチ（Apple Watch / Wear OS）のOS標準ミラー機能でも表示され得る
// 通知アクションボタンを追加するための共通定義。
//
// 参照: 詳細設計「機能見直し-1-#6 - スマートウォッチへの休憩タイマー通知（片方向連携）_設計書」1-2節
// QA確定: [[training-app/機能見直し-1_QA]] Q6-2（操作ボタンを付ける）

import * as Notifications from 'expo-notifications';

export const REST_TIMER_CATEGORY_ID = 'REST_TIMER_ACTIONS';
export const EXTEND_ACTION_ID = 'EXTEND_30S';
export const SKIP_ACTION_ID = 'SKIP';
/** 延長ボタン押下時に伸ばす秒数（設計書1-2節のコード例に準拠） */
export const EXTEND_SECONDS = 30;

/**
 * アプリ起動時に1回だけ呼び出す。
 * カテゴリ登録は冪等なので複数回呼んでも問題ない。
 */
export async function registerRestTimerNotificationCategory(): Promise<void> {
  await Notifications.setNotificationCategoryAsync(REST_TIMER_CATEGORY_ID, [
    {
      identifier: EXTEND_ACTION_ID,
      buttonTitle: `延長 (+${EXTEND_SECONDS}秒)`,
      options: { opensAppToForeground: false },
    },
    {
      identifier: SKIP_ACTION_ID,
      buttonTitle: 'スキップ',
      options: { opensAppToForeground: false, isDestructive: true },
    },
  ]);
}
