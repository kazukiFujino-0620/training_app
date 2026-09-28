import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import DayItemsSheet from '../DayItemsSheet';
import { masterApi } from '../../api/client';
import { dayBeforeMonthDay, nextStartDate, fmtMonthDay } from '../PeriodizationShared';

// 機能見直し-1-#3: 曜日別の種目編集シート（1枚のModal内で追加・削除・セット数・並べ替え・部位選択まで完結）

jest.mock('../../api/client', () => ({
  masterApi: { getItems: jest.fn() },
}));

beforeEach(() => {
  jest.clearAllMocks();
  (masterApi.getItems as jest.Mock).mockResolvedValue({
    data: [
      { id: 1, partCode: 'CHEST', itemName: 'ベンチプレス', displayOrder: 1 },
      { id: 2, partCode: 'CHEST', itemName: 'ディップス', displayOrder: 2 },
      { id: 3, partCode: 'LEG', itemName: 'スクワット', displayOrder: 1 },
    ],
  });
});

describe('DayItemsSheet', () => {
  it('種目の追加・セット数変更・並べ替えを反映して保存する', async () => {
    const onSave = jest.fn().mockResolvedValue(undefined);
    await render(
      <DayItemsSheet
        visible
        title="月曜日の種目編集"
        subTitle="第1週・胸"
        editablePart={false}
        partCode="CHEST"
        items={[{ itemName: 'ベンチプレス', targetSets: 3 }]}
        onClose={jest.fn()}
        onSave={onSave}
      />,
    );

    await fireEvent.changeText(screen.getByPlaceholderText('＋ 種目を追加（名前を入力）'), 'ディップ');
    await waitFor(() => expect(screen.getByText('ディップス')).toBeTruthy());
    await fireEvent.press(screen.getByText('ディップス'));

    await fireEvent.press(screen.getAllByLabelText('セット数を増やす')[1]);
    await fireEvent.press(screen.getByLabelText('ディップスを上へ'));
    await fireEvent.press(screen.getByText('保存する'));

    await waitFor(() => expect(onSave).toHaveBeenCalledWith(
      [{ itemName: 'ディップス', targetSets: 4 }, { itemName: 'ベンチプレス', targetSets: 3 }],
      'CHEST',
    ));
  });

  it('保存に失敗したらサーバーのメッセージを表示して閉じない', async () => {
    const onSave = jest.fn().mockRejectedValue({ response: { data: { error: '種目マスタに存在しない種目です: X' } } });
    const onClose = jest.fn();
    await render(
      <DayItemsSheet
        visible title="月曜日" subTitle="第1週" editablePart={false} partCode="CHEST"
        items={[{ itemName: 'ベンチプレス', targetSets: 3 }]} onClose={onClose} onSave={onSave}
      />,
    );
    await fireEvent.press(screen.getByText('保存する'));
    await waitFor(() => expect(screen.getByText('種目マスタに存在しない種目です: X')).toBeTruthy());
    expect(onClose).not.toHaveBeenCalled();
  });

  it('白紙作成では部位をシート内で選べ、休養にすると種目は空で保存する', async () => {
    const onSave = jest.fn().mockResolvedValue(undefined);
    await render(
      <DayItemsSheet
        visible title="火曜日" subTitle="第1週" editablePart partCode="LEG"
        items={[{ itemName: 'スクワット', targetSets: 5 }]} onClose={jest.fn()} onSave={onSave}
      />,
    );
    await fireEvent.press(screen.getByText('休養'));
    await fireEvent.press(screen.getByText('保存する'));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith([], null));
  });
});

describe('PeriodizationShared（日付）', () => {
  it('次のサイクルの開始日は開始日+週数×7日（終了日の翌日）', () => {
    expect(nextStartDate('2026-09-26', 4)).toBe('2026-10-24');
    expect(dayBeforeMonthDay('2026-10-24')).toBe('10/23');
    expect(fmtMonthDay('2026-10-05')).toBe('10/5');
  });
});
