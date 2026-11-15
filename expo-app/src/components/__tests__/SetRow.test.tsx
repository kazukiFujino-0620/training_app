import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { Alert } from 'react-native';
import SetRow from '../SetRow';
import { trainingApi } from '../../api/client';
import type { TrainingDetail } from '../../api/types';

// 機能見直し-1-#18: 本番セットの完了時に任意で「あと何回できたか（RIR）」を選ぶ欄と、
// 完了済みセットの余力表示・選び直しを検証する。

jest.mock('../../api/client', () => ({
  trainingApi: {
    updateSet: jest.fn(),
    updateSetRir: jest.fn(),
  },
}));

const baseDetail: TrainingDetail = {
  id: 1,
  setNumber: 1,
  setType: 'MAIN',
  weight: 82.5,
  reps: 10,
  completed: false,
  durationMin: null,
  distanceKm: null,
  avgHeartRateBpm: null,
  caloriesKcal: null,
  rir: null,
};

const PICKER_TITLE = 'あと何回できましたか？（任意）';

beforeEach(() => {
  jest.clearAllMocks();
  (trainingApi.updateSet as jest.Mock).mockResolvedValue({
    data: { id: 1, completed: true, PR: false },
  });
  (trainingApi.updateSetRir as jest.Mock).mockResolvedValue({ data: {} });
});

describe('SetRow: RIR（余力）', () => {
  it('本番セットを完了すると選択欄が出て、選んだ値を保存し表示する', async () => {
    const onUpdated = jest.fn();
    await render(<SetRow detail={baseDetail} onUpdated={onUpdated} />);

    expect(screen.queryByText(PICKER_TITLE)).toBeNull();
    await fireEvent.press(screen.getByLabelText('セット完了'));
    expect(await screen.findByText(PICKER_TITLE)).toBeTruthy();
    expect(screen.getByText('5以上')).toBeTruthy();
    expect(screen.getByText('スキップ')).toBeTruthy();

    await fireEvent.press(screen.getByText('2'));

    await waitFor(() => expect(trainingApi.updateSetRir).toHaveBeenCalledWith(1, 2));
    expect(screen.queryByText(PICKER_TITLE)).toBeNull();
    expect(screen.getByText('あと2回')).toBeTruthy();
    expect(onUpdated).toHaveBeenLastCalledWith(expect.objectContaining({ rir: 2 }));
  });

  it('「5以上」は5として保存し「あと5回以上」と表示する', async () => {
    await render(<SetRow detail={baseDetail} onUpdated={jest.fn()} />);

    await fireEvent.press(screen.getByLabelText('セット完了'));
    await fireEvent.press(await screen.findByText('5以上'));

    await waitFor(() => expect(trainingApi.updateSetRir).toHaveBeenCalledWith(1, 5));
    expect(screen.getByText('あと5回以上')).toBeTruthy();
  });

  it('スキップすると何も保存せずに閉じ、未入力の表示になる', async () => {
    await render(<SetRow detail={baseDetail} onUpdated={jest.fn()} />);

    await fireEvent.press(screen.getByLabelText('セット完了'));
    await fireEvent.press(await screen.findByText('スキップ'));

    expect(screen.queryByText(PICKER_TITLE)).toBeNull();
    expect(trainingApi.updateSetRir).not.toHaveBeenCalled();
    expect(screen.getByText('あと?回')).toBeTruthy();
  });

  it('ウォームアップのセットでは完了しても選択欄も表示も出ない', async () => {
    await render(<SetRow detail={{ ...baseDetail, setType: 'WARMUP' }} onUpdated={jest.fn()} />);

    await fireEvent.press(screen.getByLabelText('セット完了'));

    await waitFor(() => expect(trainingApi.updateSet).toHaveBeenCalled());
    expect(screen.queryByText(PICKER_TITLE)).toBeNull();
    expect(screen.queryByText('あと?回')).toBeNull();
  });

  it('未完了のセットには余力の表示を出さない', async () => {
    await render(<SetRow detail={{ ...baseDetail, rir: 3 }} onUpdated={jest.fn()} />);

    expect(screen.queryByText('あと3回')).toBeNull();
    expect(screen.queryByText('あと?回')).toBeNull();
  });

  it('完了済みセットの表示を押すと選び直せる', async () => {
    await render(
      <SetRow detail={{ ...baseDetail, completed: true, rir: 3 }} onUpdated={jest.fn()} />,
    );

    await fireEvent.press(screen.getByText('あと3回'));
    expect(screen.getByText(PICKER_TITLE)).toBeTruthy();
    // 選び直しのときは「スキップ」ではなく「記録しない」
    expect(screen.queryByText('スキップ')).toBeNull();

    await fireEvent.press(screen.getByText('4'));

    await waitFor(() => expect(trainingApi.updateSetRir).toHaveBeenCalledWith(1, 4));
    expect(screen.getByText('あと4回')).toBeTruthy();
  });

  it('未入力のまま完了したセットにもあとから記録できる', async () => {
    await render(<SetRow detail={{ ...baseDetail, completed: true }} onUpdated={jest.fn()} />);

    await fireEvent.press(screen.getByText('あと?回'));
    await fireEvent.press(screen.getByText('0'));

    await waitFor(() => expect(trainingApi.updateSetRir).toHaveBeenCalledWith(1, 0));
    expect(screen.getByText('あと0回')).toBeTruthy();
  });

  it('「記録しない」で未入力に戻す', async () => {
    await render(
      <SetRow detail={{ ...baseDetail, completed: true, rir: 2 }} onUpdated={jest.fn()} />,
    );

    await fireEvent.press(screen.getByText('あと2回'));
    await fireEvent.press(screen.getByText('記録しない'));

    await waitFor(() => expect(trainingApi.updateSetRir).toHaveBeenCalledWith(1, null));
    expect(screen.getByText('あと?回')).toBeTruthy();
  });

  it('保存に失敗したら元の値に戻してエラーを出す', async () => {
    (trainingApi.updateSetRir as jest.Mock).mockRejectedValue({
      response: { data: { error: 'RIRは0〜5で指定してください' } },
    });
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});
    await render(
      <SetRow detail={{ ...baseDetail, completed: true, rir: 2 }} onUpdated={jest.fn()} />,
    );

    await fireEvent.press(screen.getByText('あと2回'));
    await fireEvent.press(screen.getByText('4'));

    await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('エラー', 'RIRは0〜5で指定してください'));
    expect(screen.getByText('あと2回')).toBeTruthy();
    alertSpy.mockRestore();
  });
});
