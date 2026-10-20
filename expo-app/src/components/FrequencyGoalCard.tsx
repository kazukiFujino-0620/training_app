import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { MobileFrequencyGoalResponse } from '../api/types';

type Props = {
  goal: MobileFrequencyGoalResponse;
  onPress: () => void;
};

/**
 * カレンダータブ統計欄の「週の目標」カード（機能見直し-1-#7、仮画面パターンA〜E）。
 * 達成率は終わった週だけで計算した値をサーバーから受け取り、今週は進捗だけを中立に表示する。
 * 「あと◯回」等の急がせる文言は出さない。タップで設定シートを開く。
 */
export default function FrequencyGoalCard({ goal, onPress }: Props) {
  if (!goal.goalSet) {
    return (
      <TouchableOpacity
        style={styles.card}
        onPress={onPress}
        accessibilityRole="button"
        testID="frequency-goal-card-unset"
      >
        <View style={styles.setupRow}>
          <Text style={styles.setupText}>週の回数目標を決める</Text>
          <Text style={styles.setupArrow}>設定する →</Text>
        </View>
        <Text style={styles.sub}>例：週3回。予定どおり続けられたかを表示します</Text>
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      testID="frequency-goal-card"
    >
      <View style={styles.labelRow}>
        <Text style={styles.label}>週の目標</Text>
        {goal.fromProgram && <Text style={styles.programBadge}>プログラム</Text>}
      </View>
      <View style={styles.topRow}>
        <Text style={styles.goal}>{goal.goalLabel ?? ''}</Text>
        <Text style={styles.week}>{goal.thisWeekLabel}</Text>
      </View>
      <View style={styles.result}>
        <Text style={styles.resultText}>
          {goal.resultLabel}
          {goal.percentLabel != null && <Text style={styles.pct}> {goal.percentLabel}</Text>}
        </Text>
        {goal.notes.map((n) => (
          <Text key={n} style={styles.sub}>{n}</Text>
        ))}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    marginTop: 8, marginHorizontal: 14, backgroundColor: '#fff', borderRadius: 10, padding: 10,
  },
  labelRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 4 },
  label: { fontSize: 11, color: '#999' },
  programBadge: {
    marginLeft: 4, fontSize: 9.5, fontWeight: '700', color: '#4f46e5', backgroundColor: '#eef2ff',
    borderRadius: 4, paddingHorizontal: 6, paddingVertical: 1, overflow: 'hidden',
  },
  topRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: 6 },
  goal: { fontSize: 16, fontWeight: '800', color: '#333' },
  week: { fontSize: 12.5, color: '#555', fontWeight: '700' },
  result: { marginTop: 6, paddingTop: 6, borderTopWidth: 1, borderTopColor: '#f0f0f0' },
  resultText: { fontSize: 13, color: '#333', fontWeight: '700' },
  pct: { color: '#1f8a4c' },
  sub: { fontSize: 11.5, color: '#999', marginTop: 2 },
  setupRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  setupText: { fontSize: 13, fontWeight: '700', color: '#333' },
  setupArrow: { fontSize: 11.5, color: '#999' },
});
