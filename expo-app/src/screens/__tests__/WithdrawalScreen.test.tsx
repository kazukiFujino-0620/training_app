import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import WithdrawalScreen from '../WithdrawalScreen';
import { withdrawalApi } from '../../api/client';
import { clearTokens } from '../../auth/tokenStore';

// useFocusEffect はNavigationContainer配下でないと動作しないため、単体テストではuseEffectと同等に扱う
jest.mock('@react-navigation/native', () => {
  const { useEffect } = require('react');
  return {
    ...jest.requireActual('@react-navigation/native'),
    useFocusEffect: (effect: () => void) => useEffect(effect, []),
  };
});

// ita3-3: モバイル退会画面の単体テスト
// 対象: src/screens/WithdrawalScreen.tsx
// 一般ユーザー（招待コードなし登録）は即時削除、ジム所属ユーザーは申請制、の分岐を検証する。

jest.mock('../../api/client', () => ({
  withdrawalApi: {
    getStatus: jest.fn(),
    request: jest.fn(),
    cancel: jest.fn(),
    deleteImmediately: jest.fn(),
  },
}));

jest.mock('../../auth/tokenStore', () => ({
  clearTokens: jest.fn(),
}));

const navigation = { goBack: jest.fn(), replace: jest.fn() } as any;

beforeEach(() => {
  jest.clearAllMocks();
});

describe('WithdrawalScreen: ジム所属ユーザー（申請制）', () => {
  beforeEach(() => {
    (withdrawalApi.getStatus as jest.Mock).mockResolvedValue({
      data: { isGeneralUser: false, hasPendingRequest: false },
    });
  });

  it('タイトル・退会理由の選択肢・申請ボタンが表示される', async () => {
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    expect(screen.getByText('退会申請')).toBeTruthy();
    expect(screen.getByText('使わなくなった')).toBeTruthy();
    expect(screen.getByText('退会を申請する')).toBeTruthy();
  });

  it('チェックを入れるまで申請ボタンは無効', async () => {
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    await fireEvent.press(screen.getByText('退会を申請する'));
    expect(withdrawalApi.request).not.toHaveBeenCalled();
  });

  it('チェックを入れて送信するとrequest APIが呼ばれる', async () => {
    (withdrawalApi.request as jest.Mock).mockResolvedValue({});
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    await fireEvent.press(screen.getByTestId('withdrawal-agree-checkbox'));
    await fireEvent.press(screen.getByTestId('withdrawal-submit-button'));

    await waitFor(() => expect(withdrawalApi.request).toHaveBeenCalledWith(undefined, undefined));
    // 申請制フローでは即時ログアウトはしない
    expect(clearTokens).not.toHaveBeenCalled();
  });

  it('申請中の場合はキャンセルボタンが表示され、退会理由フォームは表示されない', async () => {
    (withdrawalApi.getStatus as jest.Mock).mockResolvedValue({
      data: { isGeneralUser: false, hasPendingRequest: true },
    });
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    expect(screen.getByText('申請中')).toBeTruthy();
    expect(screen.getByText('申請をキャンセルする')).toBeTruthy();
    expect(screen.queryByText('使わなくなった')).toBeNull();
  });
});

describe('WithdrawalScreen: 一般ユーザー（即時削除）', () => {
  beforeEach(() => {
    (withdrawalApi.getStatus as jest.Mock).mockResolvedValue({
      data: { isGeneralUser: true, hasPendingRequest: false },
    });
  });

  it('タイトルは「アカウント削除」、退会理由の選択肢は表示されない', async () => {
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    expect(screen.getByText('アカウント削除')).toBeTruthy();
    expect(screen.getByText('アカウントを削除する')).toBeTruthy();
    expect(screen.queryByText('使わなくなった')).toBeNull();
  });

  it('チェックを入れて送信するとdeleteImmediatelyが呼ばれ、トークン破棄後にAuthへ遷移する', async () => {
    (withdrawalApi.deleteImmediately as jest.Mock).mockResolvedValue({});
    await render(<WithdrawalScreen navigation={navigation} />);
    await waitFor(() => expect(withdrawalApi.getStatus).toHaveBeenCalled());

    await fireEvent.press(screen.getByTestId('withdrawal-agree-checkbox'));
    await fireEvent.press(screen.getByText('アカウントを削除する'));

    await waitFor(() => expect(withdrawalApi.deleteImmediately).toHaveBeenCalled());
    await waitFor(() => expect(clearTokens).toHaveBeenCalled());
    expect(navigation.replace).toHaveBeenCalledWith('Auth');
    // 一般ユーザーはWeb版の申請制APIを使わない
    expect(withdrawalApi.request).not.toHaveBeenCalled();
  });
});
