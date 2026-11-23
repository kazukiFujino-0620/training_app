import React from 'react';
import { fireEvent, render } from '@testing-library/react-native';
import GoalProposalCard from '../GoalProposalCard';
import type { PendingGoalProposal } from '../../api/types';

const base: PendingGoalProposal = {
  id: 9,
  title: '山本 健トレーナーから目標の提案があります',
  proposerName: '山本 健',
  currentWeeklyLabel: '週2回',
  proposedWeeklyLabel: '週3回',
  weeklyChanged: true,
  currentGoalModeLabel: '維持',
  proposedGoalModeLabel: '筋肥大',
  goalModeChanged: true,
  comment: 'まずは週3回を4週続けましょう',
  nextReviewDate: '2026-12-22',
  nextReviewLabel: '12/22',
};

describe('GoalProposalCard（機能見直し-1-#20）', () => {
  it('今の目標と提案を並べて出し、承認と見送りのボタンを押せる', async () => {
    const onAccept = jest.fn();
    const onDecline = jest.fn();
    const { getByText, getByTestId } = await render(
      <GoalProposalCard proposal={base} responding={false} onAccept={onAccept} onDecline={onDecline} />,
    );
    expect(getByText('山本 健トレーナーから目標の提案があります')).toBeTruthy();
    expect(getByText(/週2回 →/)).toBeTruthy();
    expect(getByText('週3回')).toBeTruthy();
    expect(getByText(/維持 →/)).toBeTruthy();
    expect(getByText('筋肥大')).toBeTruthy();
    expect(getByText('まずは週3回を4週続けましょう')).toBeTruthy();
    expect(getByText('12/22')).toBeTruthy();

    await fireEvent.press(getByText('この目標にする'));
    expect(onAccept).toHaveBeenCalled();
    await fireEvent.press(getByTestId('goal-proposal-decline'));
    expect(onDecline).toHaveBeenCalled();
    expect(getByText('今回は見送る')).toBeTruthy();
  });

  it('変わらない項目は矢印を出さず、ひとことが無ければ欄を出さない', async () => {
    const { queryByText, getByText } = await render(
      <GoalProposalCard
        proposal={{ ...base, weeklyChanged: false, currentWeeklyLabel: '週3回', comment: null }}
        responding={false}
        onAccept={jest.fn()}
        onDecline={jest.fn()}
      />,
    );
    expect(queryByText(/週3回 →/)).toBeNull();
    expect(getByText('週3回')).toBeTruthy();
    expect(queryByText('ひとこと')).toBeNull();
  });

  it('返事を送っている間はボタンを押しても呼ばない', async () => {
    const onAccept = jest.fn();
    const onDecline = jest.fn();
    const { getByTestId } = await render(
      <GoalProposalCard proposal={base} responding onAccept={onAccept} onDecline={onDecline} />,
    );
    await fireEvent.press(getByTestId('goal-proposal-accept'));
    await fireEvent.press(getByTestId('goal-proposal-decline'));
    expect(onAccept).not.toHaveBeenCalled();
    expect(onDecline).not.toHaveBeenCalled();
  });
});
