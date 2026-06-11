import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

interface Props {
  completed: number;
  total: number;
}

export default function ProgressBar({ completed, total }: Props) {
  const pct = total > 0 ? Math.min(completed / total, 1) : 0;
  return (
    <View style={styles.container}>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${pct * 100}%` }]} />
      </View>
      <Text style={styles.label}>
        {completed} / {total} セット完了
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { marginVertical: 8 },
  track: {
    height: 8,
    backgroundColor: '#e0e0e0',
    borderRadius: 4,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    backgroundColor: '#4CAF50',
    borderRadius: 4,
  },
  label: {
    marginTop: 4,
    fontSize: 12,
    color: '#666',
    textAlign: 'right',
  },
});
