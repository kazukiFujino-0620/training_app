import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import type { PendingGoalProposal } from '../api/types';

type Props = {
  proposal: PendingGoalProposal;
  /** 返事を送っている間はボタンを押せなくする */
  responding: boolean;
  onAccept: () => void;
  onDecline: () => void;
};

/**
 * 機能見直し-1-#20: トレーナーからの目標の提案（トップの期分けの案のバナーと同じ場所）。
 * 「この目標にする」で週の回数目標と目的が確定し、今週は#7の「目標を設定した週」として達成率に含めない。
 */
export default function GoalProposalCard({ proposal, responding, onAccept, onDecline }: Props) {
  return (
    <View style={styles.card} testID="goal-proposal-card">
      <Text style={styles.title}>{proposal.title}</Text>

      <Text style={styles.label}>週の回数目標</Text>
      <Text style={styles.value}>
        {proposal.weeklyChanged ? `${proposal.currentWeeklyLabel} → ` : ''}
        <Text style={styles.bold}>{proposal.proposedWeeklyLabel}</Text>
      </Text>

      <Text style={styles.label}>トレーニング目的</Text>
      <Text style={styles.value}>
        {proposal.goalModeChanged ? `${proposal.currentGoalModeLabel} → ` : ''}
        <Text style={styles.bold}>{proposal.proposedGoalModeLabel}</Text>
      </Text>

      {proposal.comment ? (
        <>
          <Text style={styles.label}>ひとこと</Text>
          <Text style={styles.value}>{proposal.comment}</Text>
        </>
      ) : null}

      <Text style={styles.label}>次の見直し日</Text>
      <Text style={styles.value}>{proposal.nextReviewLabel}</Text>

      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.button, styles.accept, responding && styles.disabled]}
          onPress={onAccept}
          disabled={responding}
          testID="goal-proposal-accept"
        >
          {responding ? (
            <ActivityIndicator size="small" color="#fff" />
          ) : (
            <Text style={styles.acceptText}>この目標にする</Text>
          )}
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, styles.decline, responding && styles.disabled]}
          onPress={onDecline}
          disabled={responding}
          testID="goal-proposal-decline"
        >
          <Text style={styles.declineText}>今回は見送る</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff', borderWidth: 1.5, borderColor: '#4CAF50', borderRadius: 10,
    marginHorizontal: 16, marginTop: 10, paddingHorizontal: 14, paddingVertical: 12,
  },
  title: { fontSize: 14, fontWeight: '700', color: '#222', marginBottom: 4 },
  label: { fontSize: 11, color: '#888', marginTop: 6 },
  value: { fontSize: 13, color: '#333' },
  bold: { fontWeight: '700', color: '#222' },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 12 },
  button: {
    borderRadius: 8, borderWidth: 1.5, paddingHorizontal: 14, paddingVertical: 8,
    minWidth: 120, alignItems: 'center',
  },
  accept: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  acceptText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  decline: { backgroundColor: '#fff', borderColor: '#ccc' },
  declineText: { color: '#777', fontSize: 13, fontWeight: '700' },
  disabled: { opacity: 0.6 },
});
