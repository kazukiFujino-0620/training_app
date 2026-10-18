import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { Feather } from '@expo/vector-icons';
import type { HealthSummaryResponse } from '../api/types';
import { formatSleepMinutes, restingHrCardLabel, sleepCardLabel } from '../health/referenceFormat';

type Props = {
  summary: HealthSummaryResponse | null;
  /** ローカル日付のYYYY-MM-DD */
  today: string;
  onPress: () => void;
};

/**
 * トレーニングタブの「睡眠・安静時心拍」簡易カード（機能見直し-1-#5）。
 * 最新値を1行だけ表示し、4週間平均や平均との差はヘルスケア画面で見せる（USER要望で分担）。
 */
export default function HealthReferenceCard({ summary, today, onPress }: Props) {
  const sleep = summary?.sleep ?? null;
  const rhr = summary?.restingHeartRate ?? null;

  if (!sleep && !rhr) {
    return (
      <TouchableOpacity
        style={[styles.card, styles.cardEmpty]}
        onPress={onPress}
        accessibilityRole="button"
        testID="health-reference-card-empty"
      >
        <Text style={styles.emptyText}>睡眠・安静時心拍を記録する</Text>
        <Text style={styles.chevron}>›</Text>
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityHint="ヘルスケア画面で詳細の確認と入力ができます"
      testID="health-reference-card"
    >
      <View style={styles.row}>
        {sleep && (
          <View style={styles.item}>
            <Feather name="moon" size={14} color="#666" />
            <Text style={styles.label}>{sleepCardLabel(sleep.date, today)}</Text>
            <Text style={styles.value}>{formatSleepMinutes(sleep.durationMinutes)}</Text>
          </View>
        )}
        {rhr && (
          <View style={styles.item}>
            <Feather name="heart" size={14} color="#666" />
            <Text style={styles.label}>{restingHrCardLabel(rhr.date, today)}</Text>
            <Text style={styles.value}>{rhr.restingBpm} bpm</Text>
          </View>
        )}
      </View>
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginHorizontal: 16, marginTop: 12, paddingHorizontal: 14, paddingVertical: 12,
    minHeight: 44, backgroundColor: '#fff', borderRadius: 10,
    borderWidth: 1, borderColor: '#e5e7eb',
  },
  cardEmpty: { borderStyle: 'dashed', borderColor: '#ccc' },
  row: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', columnGap: 14, rowGap: 4, flex: 1 },
  item: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  label: { fontSize: 12, color: '#666' },
  value: { fontSize: 14, fontWeight: '700', color: '#222' },
  emptyText: { fontSize: 13, color: '#666' },
  chevron: { fontSize: 16, color: '#999', marginLeft: 8 },
});
