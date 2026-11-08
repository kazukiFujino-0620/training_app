import React from 'react';
import { Alert } from 'react-native';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import AutoWorkoutGenerationScreen, { programBannerText } from '../AutoWorkoutGenerationScreen';
import AutoWorkoutResultScreen from '../AutoWorkoutResultScreen';
import { workoutGenerationApi, masterApi } from '../../api/client';
import type { AutogenForm, AutogenResult } from '../../api/types';

// 機能見直し-1-#4: メニュー自動作成（条件入力・結果）

jest.mock('../../api/client', () => ({
  workoutGenerationApi: {
    form: jest.fn(),
    generate: jest.fn(),
    addExclusion: jest.fn(),
    removeExclusion: jest.fn(),
    confirmExclusion: jest.fn(),
  },
  masterApi: { getItems: jest.fn() },
}));

const baseForm: AutogenForm = {
  minAvailableMinutes: 15,
  parts: [
    { partCode: 'CHEST', partName: '胸' },
    { partCode: 'BACK', partName: '背中' },
    { partCode: 'ARM', partName: '腕' },
  ],
  equipment: [
    { id: 1, name: 'バーベル', category: 'FREE_WEIGHT' },
    { id: 2, name: 'ダンベル', category: 'FREE_WEIGHT' },
  ],
  exclusions: [
    {
      itemName: 'ベンチプレス', partCode: 'CHEST', partName: '胸', reason: '右肩に違和感',
      excludedSince: '2026-10-26', nextConfirmationDueAt: '2026-11-09T09:00:00', needsReconfirmation: true,
    },
  ],
  program: null,
};

const okResult: AutogenResult = {
  status: 'OK',
  mode: 'ADHOC',
  program: null,
  intensityPct: 70,
  availableMinutes: 60,
  estimatedMinutes: 53,
  items: [
    {
      itemName: 'インクラインダンベルプレス', partCode: 'CHEST', partName: '胸', compound: true, bodyweight: false,
      sets: 4, reps: 10, weightKg: 24, restSeconds: 120, reason: '胸を指定。複合種目を先にしています', warning: null,
      alternatives: [
        { itemName: 'ダンベルフライ', partCode: 'CHEST', compound: false, bodyweight: false, reps: 10, weightKg: 12, restSeconds: 90, reason: '胸を指定。単関節種目は後にしています' },
      ],
    },
    {
      itemName: 'チェストプレス', partCode: 'CHEST', partName: '胸', compound: true, bodyweight: false,
      sets: 4, reps: 10, weightKg: null, restSeconds: 120,
      reason: '記録が無いため重量は空欄です。目標回数をあと3〜4回はできる余裕を残して終えられる重量で始めてください', warning: null,
      alternatives: [],
    },
    {
      itemName: 'EZバーカール', partCode: 'ARM', partName: '腕', compound: false, bodyweight: false,
      sets: 4, reps: 10, weightKg: 25, restSeconds: 90, reason: '腕を指定。単関節種目は後にしています',
      warning: 'この部位は疲労が残っています。重量・セット数を調整してください', alternatives: [],
    },
  ],
  itemsNeedingReconfirmation: [],
};

function makeNavigation() {
  return {
    navigate: jest.fn(),
    goBack: jest.fn(),
    dispatch: jest.fn(),
    getState: jest.fn(() => ({ routes: [{ key: 'list-1', name: 'TrainingList', params: undefined }] })),
  } as any;
}

beforeEach(() => {
  jest.clearAllMocks();
  (workoutGenerationApi.form as jest.Mock).mockResolvedValue({ data: baseForm });
  (masterApi.getItems as jest.Mock).mockResolvedValue({ data: [] });
});

describe('AutoWorkoutGenerationScreen（条件入力）', () => {
  it('時間・部位・器具・除外を表示し、選んだ順に部位を送る。器具は外したものを除いて送る', async () => {
    const navigation = makeNavigation();
    (workoutGenerationApi.generate as jest.Mock).mockResolvedValue({ data: okResult });

    await render(<AutoWorkoutGenerationScreen navigation={navigation} route={{ params: { date: '2026-11-09' } } as any} />);
    await waitFor(() => expect(screen.getByText('やりたい部位（選ばなければおまかせ）')).toBeTruthy());
    expect(screen.getByText('理由: 右肩に違和感')).toBeTruthy();
    expect(screen.queryByText('15分')).toBeTruthy();
    expect(screen.queryByText('10分')).toBeNull();

    await fireEvent.press(screen.getByText('30分'));
    await fireEvent.press(screen.getByText('腕'));
    await fireEvent.press(screen.getByText('胸'));
    expect(screen.getByText('選んだ順に優先します（腕 → 胸）')).toBeTruthy();
    await fireEvent.press(screen.getByText('ダンベル'));
    await fireEvent.press(screen.getByText('メニューを組む'));

    await waitFor(() => expect(workoutGenerationApi.generate).toHaveBeenCalledWith({
      availableMinutes: 30,
      date: '2026-11-09',
      desiredPartCodes: ['ARM', 'CHEST'],
      availableEquipmentIds: [1],
    }));
    expect(navigation.navigate).toHaveBeenCalledWith('AutoWorkoutResult', { result: okResult, date: '2026-11-09' });
  });

  it('器具データが空なら器具欄を出さず、器具で絞り込まない', async () => {
    (workoutGenerationApi.form as jest.Mock).mockResolvedValue({ data: { ...baseForm, equipment: [] } });
    (workoutGenerationApi.generate as jest.Mock).mockResolvedValue({ data: okResult });

    await render(<AutoWorkoutGenerationScreen navigation={makeNavigation()} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('メニューを組む')).toBeTruthy());
    expect(screen.queryByText('使える器具')).toBeNull();

    await fireEvent.press(screen.getByText('メニューを組む'));
    await waitFor(() => expect(workoutGenerationApi.generate).toHaveBeenCalled());
    expect((workoutGenerationApi.generate as jest.Mock).mock.calls[0][0].availableEquipmentIds).toBeUndefined();
  });

  it('再確認の期限が来た除外があれば結果の前に確認し、「解除する」なら組み直してから結果へ', async () => {
    const navigation = makeNavigation();
    const withReconfirm = { ...okResult, itemsNeedingReconfirmation: [baseForm.exclusions[0]] };
    (workoutGenerationApi.generate as jest.Mock)
      .mockResolvedValueOnce({ data: withReconfirm })
      .mockResolvedValueOnce({ data: okResult });
    (workoutGenerationApi.confirmExclusion as jest.Mock).mockResolvedValue({});

    await render(<AutoWorkoutGenerationScreen navigation={navigation} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('メニューを組む')).toBeTruthy());
    await fireEvent.press(screen.getByText('メニューを組む'));

    await waitFor(() => expect(screen.getByText('この除外設定はまだ必要ですか？')).toBeTruthy());
    expect(screen.getByText('10月26日から除外中')).toBeTruthy();
    expect(navigation.navigate).not.toHaveBeenCalled();

    await fireEvent.press(screen.getByText('解除する'));
    await waitFor(() => expect(navigation.navigate).toHaveBeenCalled());
    expect(workoutGenerationApi.confirmExclusion).toHaveBeenCalledWith('ベンチプレス', false);
    expect(workoutGenerationApi.generate).toHaveBeenCalledTimes(2);
    expect(navigation.navigate).toHaveBeenCalledWith('AutoWorkoutResult', { result: okResult, date: undefined });
  });

  it('「まだ必要」なら組み直さずに最初の結果を表示する', async () => {
    const navigation = makeNavigation();
    const withReconfirm = { ...okResult, itemsNeedingReconfirmation: [baseForm.exclusions[0]] };
    (workoutGenerationApi.generate as jest.Mock).mockResolvedValue({ data: withReconfirm });
    (workoutGenerationApi.confirmExclusion as jest.Mock).mockResolvedValue({});

    await render(<AutoWorkoutGenerationScreen navigation={navigation} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('メニューを組む')).toBeTruthy());
    await fireEvent.press(screen.getByText('メニューを組む'));
    await waitFor(() => expect(screen.getByText('まだ必要')).toBeTruthy());
    await fireEvent.press(screen.getByText('まだ必要'));

    await waitFor(() => expect(navigation.navigate).toHaveBeenCalled());
    expect(workoutGenerationApi.confirmExclusion).toHaveBeenCalledWith('ベンチプレス', true);
    expect(workoutGenerationApi.generate).toHaveBeenCalledTimes(1);
    expect(navigation.navigate.mock.calls[0][1].result.itemsNeedingReconfirmation).toEqual([]);
  });

  it('プログラム実施中はその日の予定を上に表示する', () => {
    const program = {
      cycleName: '筋肥大4週', weekNumber: 2, totalWeeks: 4, targetIntensityPct: 70, partCode: 'LEG', partName: '脚',
    };
    expect(programBannerText(program, true)).toBe(
      '今日はプログラム「筋肥大4週」第2週・脚の日です（目標強度 70%）。\n部位を選ばなければ、この予定どおりに組みます。',
    );
    expect(programBannerText({ ...program, partCode: null, partName: null }, false)).toBe(
      'この日はプログラム「筋肥大4週」第2週の休養日です（目標強度 70%）。\n部位を選ばなければおまかせで組み、強度だけプログラムに合わせます。',
    );
  });

  it('プログラム実施中は部位欄の見出しと案内が変わる', async () => {
    (workoutGenerationApi.form as jest.Mock).mockResolvedValue({
      data: {
        ...baseForm,
        program: { cycleName: '筋肥大4週', weekNumber: 2, totalWeeks: 4, targetIntensityPct: 70, partCode: 'LEG', partName: '脚' },
      },
    });
    await render(<AutoWorkoutGenerationScreen navigation={makeNavigation()} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('やりたい部位（選ばなければプログラムどおり）')).toBeTruthy());
    expect(screen.getByText('部位を選ぶとそちらを優先し、強度だけプログラムに合わせます')).toBeTruthy();
    expect(screen.getByTestId('autogen-program-banner')).toBeTruthy();
  });

  it('除外の×で確認のうえ除外をやめる', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert');
    (workoutGenerationApi.removeExclusion as jest.Mock).mockResolvedValue({});
    await render(<AutoWorkoutGenerationScreen navigation={makeNavigation()} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('ベンチプレス')).toBeTruthy());

    await fireEvent.press(screen.getByLabelText('除外をやめる'));
    const buttons = alertSpy.mock.calls[0][2] as { text: string; onPress?: () => Promise<void> }[];
    await act(async () => { await buttons.find((b) => b.text === 'やめる')!.onPress!(); });

    expect(workoutGenerationApi.removeExclusion).toHaveBeenCalledWith('ベンチプレス');
    await waitFor(() => expect(screen.queryByText('ベンチプレス')).toBeNull());
    alertSpy.mockRestore();
  });

  it('除外の追加: 種目を選んで理由を書き、除外する', async () => {
    (workoutGenerationApi.form as jest.Mock).mockResolvedValue({ data: { ...baseForm, exclusions: [] } });
    (masterApi.getItems as jest.Mock).mockResolvedValue({
      data: [
        { id: 12, partCode: 'CHEST', itemName: 'ダンベルフライ', displayOrder: 2 },
        { id: 99, partCode: 'CARDIO', itemName: 'ランニングマシン', displayOrder: 1 },
      ],
    });
    (workoutGenerationApi.addExclusion as jest.Mock).mockResolvedValue({
      data: {
        itemName: 'ダンベルフライ', partCode: 'CHEST', partName: '胸', reason: '右肩に違和感がある',
        excludedSince: '2026-11-09', nextConfirmationDueAt: '2026-11-23T09:00:00', needsReconfirmation: false,
      },
    });

    await render(<AutoWorkoutGenerationScreen navigation={makeNavigation()} route={{ params: undefined } as any} />);
    await waitFor(() => expect(screen.getByText('＋ 除外する種目を追加')).toBeTruthy());
    await fireEvent.press(screen.getByText('＋ 除外する種目を追加'));
    await waitFor(() => expect(screen.getByText('ダンベルフライ')).toBeTruthy());
    expect(screen.queryByText('ランニングマシン')).toBeNull();

    await fireEvent.press(screen.getByText('ダンベルフライ'));
    await fireEvent.changeText(screen.getByPlaceholderText('例: 右肩に違和感がある'), '右肩に違和感がある');
    await fireEvent.press(screen.getByText('除外する'));

    await waitFor(() => expect(workoutGenerationApi.addExclusion).toHaveBeenCalledWith('ダンベルフライ', '右肩に違和感がある'));
    await waitFor(() => expect(screen.getByText('理由: 右肩に違和感がある')).toBeTruthy());
    // 担当トレーナーへの相談リンクは、移動先の画面がモバイルに無いため出さない
    expect(screen.queryByText(/担当トレーナーに相談/)).toBeNull();
  });
});

describe('AutoWorkoutResultScreen（結果の確認・編集）', () => {
  it('種目数・セット数・目安時間・理由・注意文を表示し、記録の無い種目の重量は空欄', async () => {
    await render(<AutoWorkoutResultScreen navigation={makeNavigation()} route={{ params: { result: okResult } } as any} />);
    expect(screen.getByText('3種目・12セット')).toBeTruthy();
    expect(screen.getByText('おまかせ')).toBeTruthy();
    expect(screen.getByText('目安 約53分（使える時間 60分）')).toBeTruthy();
    expect(screen.getByText('最初の種目は軽い重量で2セットほど体を慣らしてください。')).toBeTruthy();
    expect(screen.getByText('この部位は疲労が残っています。重量・セット数を調整してください')).toBeTruthy();
    expect(screen.getByLabelText('チェストプレスの重量').props.value).toBe('');
    expect(screen.getByLabelText('インクラインダンベルプレスの重量').props.value).toBe('24');
  });

  it('編集した重量・回数・セットを引き継いで種目追加画面を開く（条件・結果の画面は残さない）', async () => {
    const navigation = makeNavigation();
    await render(<AutoWorkoutResultScreen navigation={navigation} route={{ params: { result: okResult, date: '2026-11-10' } } as any} />);

    await fireEvent.changeText(screen.getByLabelText('インクラインダンベルプレスの重量'), '26');
    await fireEvent.changeText(screen.getByLabelText('インクラインダンベルプレスのセット'), '3');
    await fireEvent.press(screen.getAllByText('削除')[2]);
    expect(screen.getByText('2種目・7セット')).toBeTruthy();
    await fireEvent.press(screen.getByText('この内容で反映する'));

    const action = navigation.dispatch.mock.calls[0][0];
    expect(action.type).toBe('RESET');
    expect(action.payload.index).toBe(1);
    expect(action.payload.routes[0]).toMatchObject({ key: 'list-1', name: 'TrainingList' });
    expect(action.payload.routes[1]).toEqual({
      name: 'AddExercise',
      params: {
        date: '2026-11-10',
        aiSuggestion: {
          items: [
            { itemName: 'インクラインダンベルプレス', weightMin: 26, weightMax: 26, repsMin: 10, repsMax: 10, sets: 3 },
            { itemName: 'チェストプレス', weightMin: null, weightMax: null, repsMin: 10, repsMax: 10, sets: 4 },
          ],
        },
      },
    });
  });

  it('入力が不正なら反映せずに知らせる', async () => {
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    const navigation = makeNavigation();
    await render(<AutoWorkoutResultScreen navigation={navigation} route={{ params: { result: okResult } } as any} />);
    await fireEvent.changeText(screen.getByLabelText('EZバーカールの回数'), '');
    await fireEvent.press(screen.getByText('この内容で反映する'));
    expect(navigation.dispatch).not.toHaveBeenCalled();
    expect(alertSpy).toHaveBeenCalledWith('入力を確認してください', expect.stringContaining('EZバーカール'));
    alertSpy.mockRestore();
  });

  it('入れ替え: 同じ部位の候補から選ぶと、その種目の重量・回数・理由に置き換わる', async () => {
    await render(<AutoWorkoutResultScreen navigation={makeNavigation()} route={{ params: { result: okResult } } as any} />);
    // 候補のある種目だけ「入れ替え」を出す
    expect(screen.getAllByText('入れ替え')).toHaveLength(1);
    await fireEvent.press(screen.getByText('入れ替え'));
    expect(screen.getByText('インクラインダンベルプレス（胸）の代わり')).toBeTruthy();
    expect(screen.getByText('胸・単関節')).toBeTruthy();

    await fireEvent.press(screen.getByText('これにする'));
    expect(screen.queryByTestId('autogen-row-インクラインダンベルプレス')).toBeNull();
    expect(screen.getByTestId('autogen-row-ダンベルフライ')).toBeTruthy();
    expect(screen.getByLabelText('ダンベルフライの重量').props.value).toBe('12');
    expect(screen.getByText('胸を指定。単関節種目は後にしています')).toBeTruthy();
  });

  it('プログラムの強度で組んだ場合は何週目・何%かを表示する', async () => {
    const result: AutogenResult = {
      ...okResult,
      mode: 'CYCLE_COMPLEMENT',
      program: { cycleName: '筋肥大4週', weekNumber: 2, totalWeeks: 4, targetIntensityPct: 70, partCode: 'LEG', partName: '脚' },
    };
    await render(<AutoWorkoutResultScreen navigation={makeNavigation()} route={{ params: { result } } as any} />);
    expect(screen.getByText('プログラム 第2週・70%')).toBeTruthy();
  });

  it('条件に合う種目が無いときは見直しを案内する', async () => {
    const navigation = makeNavigation();
    const result: AutogenResult = { ...okResult, status: 'NO_CANDIDATES', items: [] };
    await render(<AutoWorkoutResultScreen navigation={navigation} route={{ params: { result } } as any} />);
    expect(screen.getByText('条件に合う種目が見つかりません')).toBeTruthy();
    expect(screen.getByText('器具の選択を増やすか、除外している種目を見直してください。')).toBeTruthy();
    await fireEvent.press(screen.getByText('条件を変えて組み直す'));
    expect(navigation.goBack).toHaveBeenCalled();
  });

  it('最低構成が時間に入らないときは「時間が短いため生成できません」', async () => {
    const result: AutogenResult = { ...okResult, status: 'TOO_SHORT', items: [] };
    await render(<AutoWorkoutResultScreen navigation={makeNavigation()} route={{ params: { result } } as any} />);
    expect(screen.getByText('時間が短いため生成できません')).toBeTruthy();
  });
});
