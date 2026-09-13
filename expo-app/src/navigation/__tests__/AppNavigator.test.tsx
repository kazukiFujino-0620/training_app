import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import RootNavigator from '../AppNavigator';

// itバグ-25: AppStack配下の画面をReact.lazy化した際に、
// 1) 未ログイン時に必ず表示されるAuthStack（Login）が引き続き即座に表示されること
// 2) ログイン後、AppStackの初期画面（TrainingList、遅延ロード対象）がSuspense経由で
//    エラーなく最終的に表示されること
// の2点をNavigationContainerごとマウントして検証する（実際のuseFocusEffect等の
// ナビゲーション連携込みで確認するため、個別画面の単体テストとは別に用意している）。

jest.mock('../../api/client', () => ({
  trainingApi: {
    getToday: jest.fn().mockResolvedValue({ data: [] }),
    getTodayCalories: jest.fn().mockResolvedValue({ data: { totalCalories: 0 } }),
    deleteTraining: jest.fn(),
  },
  noticeApi: {
    getActive: jest.fn().mockResolvedValue({ data: [] }),
  },
  coachingApi: {
    getTodayTrainingSuggestion: jest.fn().mockResolvedValue({ data: null }),
  },
  statsApi: {
    getTraining: jest.fn().mockResolvedValue({ data: {} }),
  },
  authApi: {
    login: jest.fn(),
  },
}));

// LoginScreenがモジュールスコープでexpo-linkingを使ってOAuthリダイレクトURLを生成しているが、
// テスト環境にはapp.jsonのURIスキーム解決に必要なネイティブmanifestが無いためモックする
// （itバグ-25の遅延ロード対応とは無関係の、テスト環境固有の前提条件）
jest.mock('expo-linking', () => ({
  createURL: jest.fn(() => 'upcurv://oauth-callback'),
}));

jest.mock('../../auth/tokenStore', () => ({
  getUserName: jest.fn().mockResolvedValue('テストユーザー'),
  clearTokens: jest.fn(),
  saveTokens: jest.fn(),
  getOrCreateDeviceId: jest.fn().mockResolvedValue('device-1'),
}));

describe('RootNavigator: itバグ-25 画面の遅延ロード', () => {
  it('未ログイン時はAuthStack（Login画面）が即座に表示される', async () => {
    await render(<RootNavigator initialRoute="Auth" />);

    expect(await screen.findByText('Upcurv')).toBeTruthy();
    expect(screen.getByText('アカウントにログイン')).toBeTruthy();
  });

  it('ログイン後はSuspense経由でAppStackの初期画面（TrainingList、遅延ロード対象）が表示される', async () => {
    await render(<RootNavigator initialRoute="App" />);

    // React.lazyの解決を待って最終的な画面内容が表示されることを確認する。
    // （Suspenseのfallback表示中はActivityIndicatorのみで、画面固有テキストはまだ存在しない）
    await waitFor(
      () => {
        expect(screen.getByText('今日のトレーニング')).toBeTruthy();
      },
      { timeout: 5000 },
    );
    // 遅延ロードしたTrainingListScreen本体（react-native-calendarsを使う画面）が
    // エラーなく最後まで描画されていることを確認する
    expect(screen.getByText('今日のトレーニングはありません')).toBeTruthy();
  });
});
