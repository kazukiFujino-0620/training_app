import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import HealthReferenceCard from '../HealthReferenceCard';
import type { HealthSummaryResponse } from '../../api/types';

jest.mock('@expo/vector-icons', () => ({ Feather: () => null }));

const base: HealthSummaryResponse = {
  weight: null, steps: null, heartRate: null, calories: null, sleep: null, restingHeartRate: null,
};

describe('HealthReferenceCard（機能見直し-1-#5）', () => {
  it('記録がないときは記録を促す1行だけを出し、タップで遷移する', async () => {
    const onPress = jest.fn();
    const { getByText, getByTestId } = await render(
      <HealthReferenceCard summary={base} today="2026-10-15" onPress={onPress} />,
    );
    expect(getByText('睡眠・安静時心拍を記録する')).toBeTruthy();
    fireEvent.press(getByTestId('health-reference-card-empty'));
    expect(onPress).toHaveBeenCalled();
  });

  it('今日の記録は「昨夜の睡眠」「安静時心拍」で表示し、平均や差は出さない', async () => {
    const summary: HealthSummaryResponse = {
      ...base,
      sleep: {
        date: '2026-10-15', startTime: null, endTime: null, durationMinutes: 390, source: 'MANUAL',
        averageMinutesPast4Weeks: 432, averageDaysPast4Weeks: 18,
      },
      restingHeartRate: {
        date: '2026-10-15', restingBpm: 58, source: 'HEALTHKIT',
        averageBpmPast4Weeks: 55, averageDaysPast4Weeks: 20,
      },
    };
    const { getByText, queryByText } = await render(
      <HealthReferenceCard summary={summary} today="2026-10-15" onPress={jest.fn()} />,
    );
    expect(getByText('昨夜の睡眠')).toBeTruthy();
    expect(getByText('6時間30分')).toBeTruthy();
    expect(getByText('安静時心拍')).toBeTruthy();
    expect(getByText('58 bpm')).toBeTruthy();
    expect(queryByText(/平均/)).toBeNull();
  });

  it('古い記録しかない場合は日付を添え、片方だけの記録でも表示する', async () => {
    const summary: HealthSummaryResponse = {
      ...base,
      sleep: {
        date: '2026-10-09', startTime: null, endTime: null, durationMinutes: 0, source: 'MANUAL',
        averageMinutesPast4Weeks: null, averageDaysPast4Weeks: 0,
      },
    };
    const { getByText, queryByText } = await render(
      <HealthReferenceCard summary={summary} today="2026-10-15" onPress={jest.fn()} />,
    );
    expect(getByText('10/9の睡眠')).toBeTruthy();
    expect(getByText('0時間0分')).toBeTruthy();
    expect(queryByText(/安静時心拍/)).toBeNull();
  });
});
