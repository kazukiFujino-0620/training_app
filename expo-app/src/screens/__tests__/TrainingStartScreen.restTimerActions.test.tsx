import React from 'react';
import { AppState } from 'react-native';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import * as Notifications from 'expo-notifications';
import TrainingStartScreen from '../TrainingStartScreen';
import { trainingApi } from '../../api/client';
import {
  REST_TIMER_CATEGORY_ID, EXTEND_ACTION_ID, SKIP_ACTION_ID,
} from '../../notifications/restTimerCategory';

// 機能見直し-1-#6: スマートウォッチへの休憩タイマー通知（片方向連携）
// 対象: src/screens/TrainingStartScreen.tsx
// 参照: training-app/詳細設計/機能見直し-1-#6 - スマートウォッチへの休憩タイマー通知（片方向連携）_設計書 1-2節
//
// スコープ（QA Q6-1〜Q6-3反映後）:
// - フォアグラウンド通知の新規追加は行わない（既存のバックグラウンド限定のまま）
// - 既存のバックグラウンド通知に「延長」「スキップ」の通知アクションボタンを追加する
// - ボタン操作の結果をインターバルタイマーのstateに反映する

// useFocusEffect / UNSTABLE_usePreventRemove はNavigationContainer配下でないと動作しないため、
// 単体テストでは useEffect 相当・no-op に置き換える（WithdrawalScreen.test.tsx と同様の手法）
jest.mock('@react-navigation/native', () => {
  const { useEffect } = require('react');
  return {
    ...jest.requireActual('@react-navigation/native'),
    useFocusEffect: (effect: () => void) => useEffect(effect, []),
    UNSTABLE_usePreventRemove: () => {},
  };
});

jest.mock('../../api/client', () => ({
  trainingApi: {
    getToday: jest.fn(),
  },
}));

jest.mock('../../auth/tokenStore', () => ({
  clearTokens: jest.fn(),
}));

jest.mock('expo-av', () => ({
  Audio: {
    Sound: {
      createAsync: jest.fn().mockResolvedValue({
        sound: {
          playAsync: jest.fn().mockResolvedValue(undefined),
          stopAsync: jest.fn().mockResolvedValue(undefined),
          unloadAsync: jest.fn().mockResolvedValue(undefined),
        },
      }),
    },
  },
}));

let capturedResponseListener: ((response: any) => void) | null = null;

jest.mock('expo-notifications', () => ({
  requestPermissionsAsync: jest.fn().mockResolvedValue({ granted: true }),
  scheduleNotificationAsync: jest.fn().mockResolvedValue('notif-id-1'),
  cancelScheduledNotificationAsync: jest.fn().mockResolvedValue(undefined),
  setNotificationCategoryAsync: jest.fn().mockResolvedValue(undefined),
  addNotificationResponseReceivedListener: jest.fn((listener: (response: any) => void) => {
    capturedResponseListener = listener;
    return { remove: jest.fn() };
  }),
  SchedulableTriggerInputTypes: { TIME_INTERVAL: 'timeInterval' },
}));

const navigation = { replace: jest.fn(), navigate: jest.fn(), dispatch: jest.fn() } as any;

function buildResponse(actionIdentifier: string) {
  return {
    actionIdentifier,
    notification: {
      request: {
        content: { categoryIdentifier: REST_TIMER_CATEGORY_ID },
      },
    },
  };
}

// AppState.addEventListener をモックし、テストから直接 'change' イベントを発火できるようにする
let appStateListener: ((state: string) => void) | null = null;

beforeEach(() => {
  jest.clearAllMocks();
  capturedResponseListener = null;
  appStateListener = null;
  (trainingApi.getToday as jest.Mock).mockResolvedValue({ data: [] });
  // jest-expo の react-native automock では AppState.currentState が実際の値（'active'等）を
  // 返さないため、コンポーネント側の「フォアグラウンド→バックグラウンド遷移」判定
  // （prevState === 'active'）が動くよう明示的に 'active' にしておく
  (AppState as any).currentState = 'active';
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_event: any, cb: any) => {
    appStateListener = cb;
    return { remove: jest.fn() } as any;
  });
});

async function renderStartedInterval() {
  await render(<TrainingStartScreen navigation={navigation} />);
  await waitFor(() => expect(trainingApi.getToday).toHaveBeenCalled());

  // インターバルタイマーのアコーディオンを開く
  await fireEvent.press(screen.getByText('インターバルタイマー'));
  // デフォルト120秒でスタート
  await fireEvent.press(screen.getByText('スタート'));
}

describe('機能見直し-1-#6: 休憩タイマー通知への操作ボタン', () => {
  it('起動時にアプリ全体で使う REST_TIMER_ACTIONS カテゴリ識別子を利用する（App.tsxで登録される値と一致）', () => {
    // TrainingStartScreen 自体はカテゴリ登録を行わない（App.tsx起動時に1回登録する設計）。
    // ここでは通知スケジュール・レスポンス判定の両方が同じ識別子定数を参照していることを保証する。
    expect(REST_TIMER_CATEGORY_ID).toBe('REST_TIMER_ACTIONS');
    expect(EXTEND_ACTION_ID).toBe('EXTEND_30S');
    expect(SKIP_ACTION_ID).toBe('SKIP');
  });

  it('バックグラウンド移行時にスケジュールする通知へ categoryIdentifier (REST_TIMER_ACTIONS) を付与する', async () => {
    await renderStartedInterval();

    await act(async () => {
      appStateListener?.('background');
    });

    await waitFor(() => expect(Notifications.scheduleNotificationAsync).toHaveBeenCalled());
    const call = (Notifications.scheduleNotificationAsync as jest.Mock).mock.calls[0][0];
    expect(call.content.categoryIdentifier).toBe(REST_TIMER_CATEGORY_ID);
  });

  it('「延長 (+30秒)」アクションを受け取るとインターバル残り時間が延長される', async () => {
    await renderStartedInterval();
    expect(capturedResponseListener).not.toBeNull();

    // 開始直後（残り2:00）から延長アクションを発火 → 概ね2:30前後まで伸びる
    await act(async () => {
      capturedResponseListener!(buildResponse(EXTEND_ACTION_ID));
    });

    await waitFor(() => {
      const matches = screen.getAllByText(/^2:(2[5-9]|30)$/);
      expect(matches.length).toBeGreaterThan(0);
    });
  });

  it('「スキップ」アクションを受け取るとインターバルが即座に終了状態になる', async () => {
    await renderStartedInterval();
    expect(capturedResponseListener).not.toBeNull();

    await act(async () => {
      capturedResponseListener!(buildResponse(SKIP_ACTION_ID));
    });

    await waitFor(() => {
      expect(screen.getByText('終了！次のセットへ')).toBeTruthy();
    });
    expect(Notifications.cancelScheduledNotificationAsync).not.toHaveBeenCalled();
  });

  it('スキップ時、バックグラウンド通知がスケジュール済みならキャンセルする', async () => {
    await renderStartedInterval();
    await act(async () => {
      appStateListener?.('background');
    });
    await waitFor(() => expect(Notifications.scheduleNotificationAsync).toHaveBeenCalled());

    await act(async () => {
      capturedResponseListener!(buildResponse(SKIP_ACTION_ID));
    });

    await waitFor(() => {
      expect(Notifications.cancelScheduledNotificationAsync).toHaveBeenCalledWith('notif-id-1');
    });
  });

  it('カテゴリ識別子が一致しない通知レスポンスは無視する（他機能の通知と誤反応しない）', async () => {
    await renderStartedInterval();

    await act(async () => {
      capturedResponseListener!({
        actionIdentifier: SKIP_ACTION_ID,
        notification: { request: { content: { categoryIdentifier: 'SOME_OTHER_CATEGORY' } } },
      });
    });

    // スキップされず、インターバルは動作中のまま（終了表示が出ない）
    expect(screen.queryByText('終了！次のセットへ')).toBeNull();
  });
});
