import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import CardioRow from '../CardioRow';
import { trainingApi } from '../../api/client';
import type { TrainingDetail } from '../../api/types';

// 機能見直し-1-#1 バグ修正: 有酸素運動のみの日にセッションタイマーが起動しない不具合の
// 対応で新設したonCompletedコールバックの単体テスト。

jest.mock('../../api/client', () => ({
  trainingApi: {
    updateSet: jest.fn(),
  },
}));

const baseDetail: TrainingDetail = {
  id: 1,
  setNumber: 1,
  setType: 'MAIN',
  weight: 0,
  reps: 0,
  completed: false,
  durationMin: null,
  distanceKm: null,
  avgHeartRateBpm: null,
  caloriesKcal: null,
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('CardioRow', () => {
  it('セット完了APIが成功した直後にonUpdatedとonCompletedの両方が呼ばれる', async () => {
    (trainingApi.updateSet as jest.Mock).mockResolvedValue({
      data: { id: 1, completed: true },
    });
    const onUpdated = jest.fn();
    const onCompleted = jest.fn();

    await render(<CardioRow detail={baseDetail} onUpdated={onUpdated} onCompleted={onCompleted} />);

    await fireEvent.press(screen.getByText('開始'));
    await fireEvent.press(screen.getByText('完了'));

    await waitFor(() => expect(trainingApi.updateSet).toHaveBeenCalled());
    await waitFor(() => expect(onUpdated).toHaveBeenCalled());
    expect(onCompleted).toHaveBeenCalledTimes(1);
  });

  it('onCompletedが未指定でもエラーにならない（オプショナル）', async () => {
    (trainingApi.updateSet as jest.Mock).mockResolvedValue({
      data: { id: 1, completed: true },
    });
    const onUpdated = jest.fn();

    await render(<CardioRow detail={baseDetail} onUpdated={onUpdated} />);

    await fireEvent.press(screen.getByText('開始'));
    await fireEvent.press(screen.getByText('完了'));

    await waitFor(() => expect(onUpdated).toHaveBeenCalled());
  });

  it('API失敗時はonCompletedを呼ばない', async () => {
    (trainingApi.updateSet as jest.Mock).mockRejectedValue(new Error('network error'));
    const onCompleted = jest.fn();

    await render(<CardioRow detail={baseDetail} onUpdated={jest.fn()} onCompleted={onCompleted} />);

    await fireEvent.press(screen.getByText('開始'));
    await fireEvent.press(screen.getByText('完了'));

    await waitFor(() => expect(trainingApi.updateSet).toHaveBeenCalled());
    expect(onCompleted).not.toHaveBeenCalled();
  });
});
