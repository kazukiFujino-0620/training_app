import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { Training } from '../api/types';

interface Props {
  training: Training;
  onPress: () => void;
  onDelete: () => void;
}

const PART_LABELS: Record<string, string> = {
  CHEST: '胸', BACK: '背中', SHOULDER: '肩',
  ARM: '腕', LEG: '脚',
};

export default function TrainingCard({ training, onPress, onDelete }: Props) {
  const completed = training.details.filter((d) => d.completed).length;
  const total = training.details.length;
  const allDone = completed === total && total > 0;

  return (
    <TouchableOpacity
      style={[styles.card, allDone && styles.cardDone]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <Text style={styles.partBadge}>
            {PART_LABELS[training.partCode] ?? training.partCode}
          </Text>
          {allDone && <Text style={styles.doneBadge}>✓ 完了</Text>}
        </View>
        <TouchableOpacity onPress={onDelete} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Text style={styles.deleteIcon}>✕</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.menu}>{training.menu}</Text>

      <View style={styles.footer}>
        <Text style={styles.setInfo}>
          {total} セット　{completed} / {total} 完了
        </Text>
        <View style={styles.progressTrack}>
          <View
            style={[
              styles.progressFill,
              { width: total > 0 ? `${(completed / total) * 100}%` : '0%' },
            ]}
          />
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 6,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 2,
  },
  cardDone: { opacity: 0.6 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  partBadge: {
    fontSize: 11,
    color: '#4CAF50',
    backgroundColor: '#e8f5e9',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
    fontWeight: '600',
  },
  doneBadge: {
    fontSize: 11,
    color: '#fff',
    backgroundColor: '#4CAF50',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
    fontWeight: '600',
  },
  deleteIcon: { fontSize: 14, color: '#bbb' },
  menu: { fontSize: 18, fontWeight: '700', color: '#222', marginBottom: 10 },
  footer: { gap: 6 },
  setInfo: { fontSize: 12, color: '#888' },
  progressTrack: {
    height: 4,
    backgroundColor: '#eee',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#4CAF50',
    borderRadius: 2,
  },
});
