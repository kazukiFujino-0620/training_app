import React from 'react';
import fs from 'fs';
import path from 'path';
import { StyleSheet } from 'react-native';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react-native';
import AddExerciseScreen from '../AddExerciseScreen';
import { masterApi, trainingApi } from '../../api/client';

// BUG-4 / BUG-9 単体テスト
// 対象: src/screens/AddExerciseScreen.tsx
// 参照: training-app/単体テスト/BUG-4_BUG-9_AddExerciseScreen部位フィルター_テスト仕様書.md

jest.mock('../../api/client', () => ({
  masterApi: { getItems: jest.fn() },
  trainingApi: {
    getTrainingHistory: jest.fn(),
    addTraining: jest.fn(),
  },
}));

const mockItems = [
  { id: 1, partCode: 'CHEST', itemName: 'ベンチプレス', displayOrder: 1 },
  { id: 2, partCode: 'BACK', itemName: 'デッドリフト', displayOrder: 2 },
];

const navigation = { goBack: jest.fn() } as any;

beforeEach(() => {
  jest.clearAllMocks();
  (masterApi.getItems as jest.Mock).mockResolvedValue({ data: mockItems });
  (trainingApi.getTrainingHistory as jest.Mock).mockResolvedValue({ data: [] });
});

describe('BUG-4: AddExerciseScreen 部位フィルターの文字化け', () => {
  it('TC-1: 部位フィルターの全ラベルが正しい日本語文字列としてレンダリングされる（文字化けしない）', async () => {
    await render(<AddExerciseScreen navigation={navigation} />);
    await waitFor(() => expect(masterApi.getItems).toHaveBeenCalled());

    const expectedLabels = ['すべて', '胸', '背中', '肩', '腕', '脚'];
    for (const label of expectedLabels) {
      expect(screen.getByText(label)).toBeTruthy();
    }
  });

  it('TC-2: 部位フィルターが flexWrap の View 実装になっており、6つの部位ラベルを直接内包している（横スクロールFlatList実装への回帰防止）', async () => {
    const { container } = await render(<AddExerciseScreen navigation={navigation} />);
    await waitFor(() => expect(masterApi.getItems).toHaveBeenCalled());

    const partRowCandidates = container.queryAll((el) => {
      if (el.type !== 'View') return false;
      const flat = StyleSheet.flatten(el.props.style);
      return !!flat && flat.flexWrap === 'wrap' && flat.flexDirection === 'row';
    });
    expect(partRowCandidates.length).toBeGreaterThan(0);

    const partRow = partRowCandidates[0];
    const partRowQueries = within(partRow);
    for (const label of ['すべて', '胸', '背中', '肩', '腕', '脚']) {
      expect(partRowQueries.getByText(label)).toBeTruthy();
    }
  });

  it('TC-3: 部位フィルターボタン押下で種目一覧が正しく絞り込まれる（表示だけでなく機能も壊れていないことの確認）', async () => {
    await render(<AddExerciseScreen navigation={navigation} />);
    await waitFor(() => expect(screen.getByText('ベンチプレス')).toBeTruthy());
    expect(screen.getByText('デッドリフト')).toBeTruthy();

    await fireEvent.press(screen.getByText('背中'));

    await waitFor(() => expect(screen.queryByText('ベンチプレス')).toBeNull());
    expect(screen.getByText('デッドリフト')).toBeTruthy();
  });
});

describe('BUG-9: AddExerciseScreen キーボード表示時のレイアウト崩れ', () => {
  async function openSetInputModal() {
    const result = await render(<AddExerciseScreen navigation={navigation} />);
    await waitFor(() => expect(screen.getByText('ベンチプレス')).toBeTruthy());

    await fireEvent.press(screen.getByText('ベンチプレス'));
    await waitFor(() => expect(screen.getByText(/次へ/)).toBeTruthy());
    await fireEvent.press(screen.getByText(/次へ/));

    await waitFor(() => expect(screen.getByText('1種目を登録')).toBeTruthy());
    return result;
  }

  it('TC-4: セット入力モーダルが KeyboardAvoidingView でラップされている（ソースコードレベルの回帰防止）', () => {
    // test-renderer の新APIではホストツリーに host component のみが現れ、
    // KeyboardAvoidingView のような合成コンポーネントはツリー上で通常の View に
    // 収束してしまい実行時には判別できない。そのため実装コード自体を対象に、
    // 「モーダル内のセット入力エリアが KeyboardAvoidingView でラップされている」
    // という2026-06-30修正（9604451）の内容が保持されているかを回帰確認する。
    const source = fs.readFileSync(
      path.resolve(__dirname, '../AddExerciseScreen.tsx'),
      'utf8',
    );
    expect(source).toMatch(/KeyboardAvoidingView/);
    expect(source).toMatch(
      /<KeyboardAvoidingView[\s\S]*?<ScrollView[\s\S]*?keyboardShouldPersistTaps="handled"/,
    );
  });

  it('TC-5: セット入力モーダルの ScrollView に keyboardShouldPersistTaps="handled" が設定されている', async () => {
    const { container } = await openSetInputModal();

    const scrollViews = container.queryAll((el) => el.type === 'RCTScrollView');
    expect(scrollViews.length).toBeGreaterThan(0);
    expect(
      scrollViews.some((sv) => sv.props.keyboardShouldPersistTaps === 'handled'),
    ).toBe(true);
  });

  it('TC-6: 種目選択→次へ→セット追加の一連の操作が壊れていない（既存機能への影響なし）', async () => {
    await openSetInputModal();

    await fireEvent.press(screen.getByText('＋ セット追加'));

    await waitFor(() => expect(screen.getByPlaceholderText('重量')).toBeTruthy());
    expect(screen.getByPlaceholderText('回数')).toBeTruthy();
  });
});
