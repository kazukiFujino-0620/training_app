import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { Linking } from 'react-native';
import FormGuideModal from '../FormGuideModal';
import { formGuideApi } from '../../api/client';

// 機能見直し-1-#2: 種目一覧・おすすめメニューからタップして開くフォーム解説モーダルの単体テスト

jest.mock('../../api/client', () => ({
  formGuideApi: { get: jest.fn() },
}));

jest.spyOn(Linking, 'openURL').mockImplementation(() => Promise.resolve(true as any));

beforeEach(() => {
  jest.clearAllMocks();
});

describe('FormGuideModal', () => {
  it('データ取得成功時、画像・関節角度の注記・注意事項を表示する', async () => {
    (formGuideApi.get as jest.Mock).mockResolvedValue({
      data: {
        itemName: 'バックスクワット',
        imageUrl: '/images/exercise-guides/back-squat.jpg',
        videoUrl: null,
        jointAngleNote: '一般的な目安であり個人差があります',
        cautions: [
          { title: 'ニーイン', description: '膝が内側に入る', reason: '一般的に指摘されている理由' },
        ],
      },
    });

    await render(<FormGuideModal visible itemName="バックスクワット" onClose={jest.fn()} />);

    await waitFor(() => expect(screen.getByText('ニーイン')).toBeTruthy());
    expect(screen.getByText('※ 一般的な目安であり個人差があります')).toBeTruthy();
    expect(screen.getByText('膝が内側に入る')).toBeTruthy();
  });

  it('動画URLがある場合「解説動画を見る」ボタンでLinking.openURLを呼ぶ', async () => {
    (formGuideApi.get as jest.Mock).mockResolvedValue({
      data: {
        itemName: 'バックスクワット',
        imageUrl: null,
        videoUrl: 'https://example.com/video',
        jointAngleNote: null,
        cautions: [],
      },
    });

    await render(<FormGuideModal visible itemName="バックスクワット" onClose={jest.fn()} />);

    await waitFor(() => expect(screen.getByText('解説動画を見る')).toBeTruthy());
    await fireEvent.press(screen.getByText('解説動画を見る'));

    expect(Linking.openURL).toHaveBeenCalledWith('https://example.com/video');
  });

  it('404（データ未投入）の場合は「準備中」表示にフォールバックする', async () => {
    (formGuideApi.get as jest.Mock).mockRejectedValue({ response: { status: 404 } });

    await render(<FormGuideModal visible itemName="対象外種目" onClose={jest.fn()} />);

    await waitFor(() => expect(screen.getByText('フォーム解説は準備中です')).toBeTruthy());
  });

  it('画像・動画とも無い場合は画像プレースホルダーを表示する', async () => {
    (formGuideApi.get as jest.Mock).mockResolvedValue({
      data: {
        itemName: 'バックスクワット',
        imageUrl: null,
        videoUrl: null,
        jointAngleNote: null,
        cautions: [],
      },
    });

    await render(<FormGuideModal visible itemName="バックスクワット" onClose={jest.fn()} />);

    await waitFor(() => expect(screen.getByText('画像は準備中です')).toBeTruthy());
  });
});
