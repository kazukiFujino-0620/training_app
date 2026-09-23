import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import ItemSettingsModal from '../ItemSettingsModal';
import { restPreferenceApi } from '../../api/client';

// 機能見直し-1-#1: 種目名横「…」ボタンから開く休憩時間・スーパーセット設定モーダルの単体テスト

jest.mock('../../api/client', () => ({
  restPreferenceApi: {
    list: jest.fn(),
    upsert: jest.fn(),
    delete: jest.fn(),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

describe('ItemSettingsModal', () => {
  it('個人上書きが無い場合はフォールバック値（システム算出値）を初期表示する', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

    await render(
      <ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} fallbackSeconds={180} />,
    );

    await waitFor(() => expect(screen.getByText('180')).toBeTruthy());
    expect(screen.getByText('システム算出値')).toBeTruthy();
  });

  it('個人上書きが無くfallbackSeconds未指定の場合はDEFAULT_REST_SECONDS(120)を表示する', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

    await render(<ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} />);

    await waitFor(() => expect(screen.getByText('120')).toBeTruthy());
  });

  it('個人上書きが既に存在する場合はその値を初期表示する', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({
      data: [{ itemName: 'ベンチプレス', restSeconds: 240 }],
    });

    await render(
      <ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} fallbackSeconds={180} />,
    );

    await waitFor(() => expect(screen.getByText('240')).toBeTruthy());
    expect(screen.getByText('個人設定（保存済み）')).toBeTruthy();
  });

  it('＋ボタンで10秒刻みに増加し「編集中」表示に切り替わる', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

    await render(
      <ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} fallbackSeconds={180} />,
    );
    await waitFor(() => expect(screen.getByText('180')).toBeTruthy());

    await fireEvent.press(screen.getByText('＋'));

    expect(screen.getByText('190')).toBeTruthy();
    expect(screen.getByText('編集中')).toBeTruthy();
  });

  it('600秒を超えて増加しない（上限クランプ）', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({
      data: [{ itemName: 'ベンチプレス', restSeconds: 595 }],
    });

    await render(<ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} />);
    await waitFor(() => expect(screen.getByText('595')).toBeTruthy());

    await fireEvent.press(screen.getByText('＋'));

    expect(screen.getByText('600')).toBeTruthy();
  });

  it('「この秒数で保存」でPUT /rest-preferences/{itemName}が呼ばれ、保存後にモーダルを閉じる', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });
    (restPreferenceApi.upsert as jest.Mock).mockResolvedValue({
      data: { itemName: 'ベンチプレス', restSeconds: 190 },
    });
    const onClose = jest.fn();
    const onSaved = jest.fn();

    await render(
      <ItemSettingsModal
        visible
        itemName="ベンチプレス"
        onClose={onClose}
        onSaved={onSaved}
        fallbackSeconds={180}
      />,
    );
    await waitFor(() => expect(screen.getByText('180')).toBeTruthy());

    await fireEvent.press(screen.getByText('＋'));
    await fireEvent.press(screen.getByText('この秒数で保存'));

    await waitFor(() => expect(restPreferenceApi.upsert).toHaveBeenCalledWith('ベンチプレス', 190));
    expect(onSaved).toHaveBeenCalledWith(190);
    expect(onClose).toHaveBeenCalled();
  });

  it('「算出値に戻す」でDELETE /rest-preferences/{itemName}が呼ばれ、フォールバック値に戻す', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({
      data: [{ itemName: 'ベンチプレス', restSeconds: 240 }],
    });
    (restPreferenceApi.delete as jest.Mock).mockResolvedValue({});
    const onClose = jest.fn();
    const onReset = jest.fn();

    await render(
      <ItemSettingsModal
        visible
        itemName="ベンチプレス"
        onClose={onClose}
        onReset={onReset}
        fallbackSeconds={180}
      />,
    );
    await waitFor(() => expect(screen.getByText('240')).toBeTruthy());

    await fireEvent.press(screen.getByText('算出値に戻す'));

    await waitFor(() => expect(restPreferenceApi.delete).toHaveBeenCalledWith('ベンチプレス'));
    expect(onReset).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('supersetを指定した場合のみスーパーセット解除セクションを表示し、解除でonUngroupとonCloseを呼ぶ', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });
    const onClose = jest.fn();
    const onUngroup = jest.fn();

    await render(
      <ItemSettingsModal
        visible
        itemName="ベンチプレス"
        onClose={onClose}
        superset={{ groupId: 999, role: 'A', onUngroup }}
      />,
    );
    await waitFor(() => expect(screen.getByText('SUPER A')).toBeTruthy());

    await fireEvent.press(screen.getByText('グループを解除する'));

    expect(onUngroup).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('supersetを指定しない場合はスーパーセット解除セクションを表示しない', async () => {
    (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

    await render(<ItemSettingsModal visible itemName="ベンチプレス" onClose={jest.fn()} />);
    await waitFor(() => expect(screen.getByText('システム算出値')).toBeTruthy());

    expect(screen.queryByText('スーパーセット')).toBeNull();
  });

  describe('supersetPicker（機能見直し-1-#1: 登録前の新規ペア作成UI）', () => {
    it('candidatesが空の場合は「他の種目を追加するとペアを組めます」と表示する', async () => {
      (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

      await render(
        <ItemSettingsModal
          visible
          itemName="ベンチプレス"
          onClose={jest.fn()}
          supersetPicker={{ candidates: [], pairedKey: null, onSelect: jest.fn(), onUnpair: jest.fn() }}
        />,
      );

      await waitFor(() =>
        expect(screen.getByText('他の種目を追加するとペアを組めます')).toBeTruthy(),
      );
    });

    it('未ペア時は候補一覧を表示し、候補をタップするとonSelectが呼ばれる', async () => {
      (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });
      const onSelect = jest.fn();

      await render(
        <ItemSettingsModal
          visible
          itemName="ベンチプレス"
          onClose={jest.fn()}
          supersetPicker={{
            candidates: [{ key: 2, name: 'デッドリフト' }, { key: 3, name: 'スクワット' }],
            pairedKey: null,
            onSelect,
            onUnpair: jest.fn(),
          }}
        />,
      );

      await waitFor(() => expect(screen.getByText('デッドリフト')).toBeTruthy());
      expect(screen.getByText('スクワット')).toBeTruthy();
      expect(screen.getByText('ペアにする種目を選んでください（休憩なしで交互に実施します）。')).toBeTruthy();

      await fireEvent.press(screen.getByLabelText('デッドリフトとペアを組む'));

      expect(onSelect).toHaveBeenCalledWith(2);
    });

    it('ペア済みの場合は現在のペアを表示し、「解除する」でonUnpairが呼ばれる', async () => {
      (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });
      const onUnpair = jest.fn();

      await render(
        <ItemSettingsModal
          visible
          itemName="ベンチプレス"
          onClose={jest.fn()}
          supersetPicker={{
            candidates: [{ key: 2, name: 'デッドリフト' }],
            pairedKey: 2,
            onSelect: jest.fn(),
            onUnpair,
          }}
        />,
      );

      await waitFor(() => expect(screen.getByText('SUPER')).toBeTruthy());
      expect(screen.getAllByText('デッドリフト').length).toBeGreaterThan(0);

      await fireEvent.press(screen.getByLabelText('スーパーセットのペアを解除'));

      expect(onUnpair).toHaveBeenCalled();
    });

    it('superset（記録済みグループ表示）と同時に指定しない場合、supersetPickerのみ表示される', async () => {
      (restPreferenceApi.list as jest.Mock).mockResolvedValue({ data: [] });

      await render(
        <ItemSettingsModal
          visible
          itemName="ベンチプレス"
          onClose={jest.fn()}
          supersetPicker={{ candidates: [], pairedKey: null, onSelect: jest.fn(), onUnpair: jest.fn() }}
        />,
      );

      await waitFor(() =>
        expect(screen.getByText('他の種目を追加するとペアを組めます')).toBeTruthy(),
      );
      expect(screen.queryByText('グループを解除する')).toBeNull();
    });
  });
});
