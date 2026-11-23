import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import ProfileScreen from '../ProfileScreen';
import { profileApi, frequencyGoalApi, goalProposalApi } from '../../api/client';

// useFocusEffect はNavigationContainer配下でないと動作しないため、単体テストではuseEffectと同等に扱う
jest.mock('@react-navigation/native', () => {
  const { useEffect } = require('react');
  return {
    ...jest.requireActual('@react-navigation/native'),
    useFocusEffect: (effect: () => void) => useEffect(effect, []),
  };
});

jest.mock('../../api/client', () => ({
  profileApi: {
    get: jest.fn(),
    update: jest.fn(),
    updateGoalMode: jest.fn(),
    updateAiAdviceConsent: jest.fn(),
  },
  frequencyGoalApi: { get: jest.fn() },
  goalProposalApi: { agreement: jest.fn() },
}));

const navigation = { goBack: jest.fn(), navigate: jest.fn() } as any;

beforeEach(() => {
  jest.clearAllMocks();
  (profileApi.get as jest.Mock).mockResolvedValue({
    data: {
      userName: '佐藤 花子', heightCm: null, weightKg: null, gender: null, birthDate: null,
      currentGoalMode: 'BULKING', aiAdviceConsent: false,
    },
  });
  (frequencyGoalApi.get as jest.Mock).mockResolvedValue({ data: { manualTarget: 3 } });
});

describe('ProfileScreen: 担当トレーナーと決めた目標（機能見直し-1-#20）', () => {
  it('承認した目標のままなら週の回数目標の下に表示と次の見直し日を出す', async () => {
    (goalProposalApi.agreement as jest.Mock).mockResolvedValue({
      data: { agreed: true, tagLabel: '担当トレーナーと決めた目標（11/24 山本 健）', nextReviewLabel: '12/22' },
    });

    await render(<ProfileScreen navigation={navigation} />);

    await waitFor(() => expect(screen.getByText('担当トレーナーと決めた目標（11/24 山本 健）')).toBeTruthy());
    expect(screen.getByText('週3回')).toBeTruthy();
    expect(screen.getByText('次の見直し日')).toBeTruthy();
    expect(screen.getByText('12/22')).toBeTruthy();
  });

  it('自分で変えたあとなどは表示も次の見直し日も出さない', async () => {
    (goalProposalApi.agreement as jest.Mock).mockResolvedValue({
      data: { agreed: false, tagLabel: null, nextReviewLabel: null },
    });

    await render(<ProfileScreen navigation={navigation} />);

    await waitFor(() => expect(screen.getByText('週3回')).toBeTruthy());
    expect(screen.queryByTestId('agreed-goal-tag')).toBeNull();
    expect(screen.queryByText('次の見直し日')).toBeNull();
  });

  it('目的を自分で変えると表示を取り直し、変更するはトップの設定シートを開く', async () => {
    (goalProposalApi.agreement as jest.Mock).mockResolvedValue({
      data: { agreed: true, tagLabel: '担当トレーナーと決めた目標（11/24 山本 健）', nextReviewLabel: '12/22' },
    });
    (profileApi.updateGoalMode as jest.Mock).mockResolvedValue({});

    await render(<ProfileScreen navigation={navigation} />);
    await waitFor(() => expect(screen.getByTestId('agreed-goal-tag')).toBeTruthy());

    await fireEvent.press(screen.getByText('減量'));
    await waitFor(() => expect(profileApi.updateGoalMode).toHaveBeenCalledWith('CUTTING'));
    await waitFor(() => expect(goalProposalApi.agreement).toHaveBeenCalledTimes(2));

    await fireEvent.press(screen.getByTestId('profile-weekly-target-change'));
    expect(navigation.navigate).toHaveBeenCalledWith('TrainingList', { openFrequencyGoal: true });
  });
});
