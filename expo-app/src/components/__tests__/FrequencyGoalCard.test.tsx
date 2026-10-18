import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import FrequencyGoalCard from '../FrequencyGoalCard';
import FrequencyGoalSheet from '../FrequencyGoalSheet';
import type { MobileFrequencyGoalResponse } from '../../api/types';

const base: MobileFrequencyGoalResponse = {
  goalSet: true,
  goalLabel: '週3回',
  thisWeekLabel: '今週 3/3回',
  resultLabel: '直近4週間 3/4週達成',
  percentLabel: '（75%）',
  notes: [],
  fromProgram: false,
  thisWeekTarget: 3,
  thisWeekDoneDays: 3,
  evaluatedWeeks: 4,
  achievedWeeks: 3,
  achievementPct: 75,
  manualTarget: 3,
  programActive: false,
  programSheetLabel: null,
};

describe('FrequencyGoalCard（機能見直し-1-#7）', () => {
  it('4週以上たった場合は目標・今週の進捗・直近4週の達成率を表示し、タップで設定を開く', async () => {
    const onPress = jest.fn();
    const { getByText, getByTestId, queryByText } = await render(
      <FrequencyGoalCard goal={base} onPress={onPress} />,
    );
    expect(getByText('週の目標')).toBeTruthy();
    expect(getByText('週3回')).toBeTruthy();
    expect(getByText('今週 3/3回')).toBeTruthy();
    expect(getByText(/直近4週間 3\/4週達成/)).toBeTruthy();
    expect(getByText(/（75%）/)).toBeTruthy();
    expect(queryByText('プログラム')).toBeNull();
    await fireEvent.press(getByTestId('frequency-goal-card'));
    expect(onPress).toHaveBeenCalled();
  });

  it('1〜3週の場合は分数と補足だけで%は出さない', async () => {
    const goal = {
      ...base,
      thisWeekLabel: '今週 1/3回',
      resultLabel: '直近2週 1/2週達成',
      percentLabel: null,
      notes: ['4週たつと達成率（%）も表示します'],
    };
    const { getByText, queryByText } = await render(<FrequencyGoalCard goal={goal} onPress={jest.fn()} />);
    expect(getByText('直近2週 1/2週達成')).toBeTruthy();
    expect(getByText('4週たつと達成率（%）も表示します')).toBeTruthy();
    expect(queryByText(/%）$/)).toBeNull();
  });

  it('プログラム実施中はラベルに印を付け、予定日数を目標にしている旨を出す', async () => {
    const goal = {
      ...base,
      goalLabel: '週4回',
      thisWeekLabel: '今週 2/4回',
      fromProgram: true,
      notes: ['プログラムの予定日数を目標にしています'],
    };
    const { getByText } = await render(<FrequencyGoalCard goal={goal} onPress={jest.fn()} />);
    expect(getByText('プログラム')).toBeTruthy();
    expect(getByText('プログラムの予定日数を目標にしています')).toBeTruthy();
  });

  it('未設定なら設定への案内だけを出す', async () => {
    const onPress = jest.fn();
    const goal = { ...base, goalSet: false, goalLabel: null, thisWeekLabel: null, resultLabel: null, percentLabel: null };
    const { getByText, getByTestId, queryByText } = await render(
      <FrequencyGoalCard goal={goal} onPress={onPress} />,
    );
    expect(getByText('週の回数目標を決める')).toBeTruthy();
    expect(getByText('設定する →')).toBeTruthy();
    expect(queryByText('週の目標')).toBeNull();
    await fireEvent.press(getByTestId('frequency-goal-card-unset'));
    expect(onPress).toHaveBeenCalled();
  });
});

describe('FrequencyGoalSheet（機能見直し-1-#7）', () => {
  it('1〜7回の範囲で選んで保存する', async () => {
    const onSave = jest.fn().mockResolvedValue(undefined);
    const { getByTestId } = await render(
      <FrequencyGoalSheet visible goal={{ ...base, manualTarget: 6 }} onClose={jest.fn()} onSave={onSave} />,
    );
    await fireEvent.press(getByTestId('frequency-goal-plus'));
    await fireEvent.press(getByTestId('frequency-goal-plus')); // 7で止まる
    expect(getByTestId('frequency-goal-value').props.children[0]).toBe(7);
    await fireEvent.press(getByTestId('frequency-goal-save'));
    expect(onSave).toHaveBeenCalledWith(7);
  });

  it('未設定なら3回から始まり、1回より下には減らせない', async () => {
    const { getByTestId } = await render(
      <FrequencyGoalSheet visible goal={{ ...base, goalSet: false, manualTarget: null }} onClose={jest.fn()} onSave={jest.fn()} />,
    );
    expect(getByTestId('frequency-goal-value').props.children[0]).toBe(3);
    await fireEvent.press(getByTestId('frequency-goal-minus'));
    await fireEvent.press(getByTestId('frequency-goal-minus'));
    await fireEvent.press(getByTestId('frequency-goal-minus'));
    expect(getByTestId('frequency-goal-value').props.children[0]).toBe(1);
  });

  it('プログラム実施中は表示のみで保存ボタンを出さない', async () => {
    const goal = {
      ...base,
      programActive: true,
      programSheetLabel: 'プログラムの実施中は、その週の予定日数（今週は4回）を目標にしています。',
    };
    const { getByText, queryByTestId } = await render(
      <FrequencyGoalSheet visible goal={goal} onClose={jest.fn()} onSave={jest.fn()} />,
    );
    expect(getByText('プログラムの実施中は、その週の予定日数（今週は4回）を目標にしています。')).toBeTruthy();
    expect(getByText('ここで決めた回数は、プログラムが終わった後の週から使われます。')).toBeTruthy();
    expect(getByText('閉じる')).toBeTruthy();
    expect(queryByTestId('frequency-goal-save')).toBeNull();
    expect(queryByTestId('frequency-goal-plus')).toBeNull();
  });
});
