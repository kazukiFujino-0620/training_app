import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { TrainingDetail } from '../api/types';
import { trainingApi } from '../api/client';

interface Props {
  detail: TrainingDetail;
  /** セッション全体の経過秒数。本アプリには種目ごとの個別タイマーが無いため、
   *  完了ボタン押下時点のセッションタイマーの値を実施時間として採用する（ita2-1）。 */
  sessionElapsedSec: number;
  onUpdated: (updated: TrainingDetail) => void;
}

/** 有酸素運動（ita2-1）の記録行。セット概念が無いため、SetRowとは別に
 *  実施時間（自動反映）・距離/平均心拍数/消費カロリー（手入力）を表示する。 */
export default function CardioRow({ detail, sessionElapsedSec, onUpdated }: Props) {
  const [distance, setDistance] = useState(detail.distanceKm != null ? String(detail.distanceKm) : '');
  const [heartRate, setHeartRate] = useState(detail.avgHeartRateBpm != null ? String(detail.avgHeartRateBpm) : '');
  const [calories, setCalories] = useState(detail.caloriesKcal != null ? String(detail.caloriesKcal) : '');
  const [loading, setLoading] = useState(false);
  const [completedLocal, setCompletedLocal] = useState(detail.completed);
  const [durationMinLocal, setDurationMinLocal] = useState(detail.durationMin);

  async function handleToggleComplete() {
    const newCompleted = !completedLocal;
    setLoading(true);
    try {
      const payload: {
        isCompleted: boolean;
        durationMin?: number;
        distanceKm?: number;
        avgHeartRateBpm?: number;
        caloriesKcal?: number;
      } = { isCompleted: newCompleted };

      let nextDurationMin = durationMinLocal;
      if (newCompleted) {
        nextDurationMin = Math.round(sessionElapsedSec / 60);
        payload.durationMin = nextDurationMin;
        if (distance !== '') payload.distanceKm = parseFloat(distance);
        if (heartRate !== '') payload.avgHeartRateBpm = parseInt(heartRate, 10);
        if (calories !== '') payload.caloriesKcal = parseFloat(calories);
      }

      const { data } = await trainingApi.updateSet(detail.id, payload);
      setCompletedLocal(data.completed);
      setDurationMinLocal(nextDurationMin);
      onUpdated({
        ...detail,
        completed: data.completed,
        durationMin: nextDurationMin ?? detail.durationMin,
        distanceKm: newCompleted && distance !== '' ? parseFloat(distance) : detail.distanceKm,
        avgHeartRateBpm: newCompleted && heartRate !== '' ? parseInt(heartRate, 10) : detail.avgHeartRateBpm,
        caloriesKcal: newCompleted && calories !== '' ? parseFloat(calories) : detail.caloriesKcal,
      });
    } catch {
      setCompletedLocal(detail.completed);
      Alert.alert('エラー', '更新に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <View style={styles.field}>
        <View style={styles.labelRow}>
          <Ionicons name="lock-closed-outline" size={12} color="#888" />
          <Text style={styles.autoLabel}>実施時間（分）— セッションタイマーから自動反映</Text>
        </View>
        <Text style={styles.autoValue}>
          {completedLocal && durationMinLocal != null
            ? `${durationMinLocal} 分`
            : '未記録（下の完了ボタンを押すと記録されます）'}
        </Text>
      </View>

      <View style={styles.field}>
        <View style={styles.labelRow}>
          <Ionicons name="create-outline" size={12} color="#555" />
          <Text style={styles.manualLabel}>距離（km）</Text>
        </View>
        <TextInput
          style={styles.manualInput}
          value={distance}
          onChangeText={setDistance}
          placeholder="マシンの表示値を入力してください"
          keyboardType="decimal-pad"
          editable={!completedLocal}
        />
      </View>

      <View style={styles.field}>
        <View style={styles.labelRow}>
          <Ionicons name="create-outline" size={12} color="#555" />
          <Text style={styles.manualLabel}>平均心拍数（bpm）</Text>
        </View>
        <TextInput
          style={styles.manualInput}
          value={heartRate}
          onChangeText={setHeartRate}
          placeholder="マシンの表示値を入力してください"
          keyboardType="number-pad"
          editable={!completedLocal}
        />
      </View>

      <View style={styles.field}>
        <View style={styles.labelRow}>
          <Ionicons name="create-outline" size={12} color="#555" />
          <Text style={styles.manualLabel}>消費カロリー（kcal）</Text>
        </View>
        <TextInput
          style={styles.manualInput}
          value={calories}
          onChangeText={setCalories}
          placeholder="マシンの表示値を入力してください"
          keyboardType="decimal-pad"
          editable={!completedLocal}
        />
      </View>

      <TouchableOpacity
        style={[styles.checkBtn, completedLocal && styles.checkBtnDone]}
        onPress={handleToggleComplete}
        disabled={loading}
      >
        <Text style={[styles.checkBtnText, completedLocal && styles.checkBtnTextDone]}>
          {completedLocal ? '✓ 完了済み' : '完了'}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { paddingVertical: 12, paddingHorizontal: 4, gap: 10 },
  field: { gap: 4 },
  labelRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  autoLabel: { fontSize: 12, fontWeight: '600', color: '#666' },
  autoValue: {
    fontSize: 14, fontWeight: '700', color: '#888',
    backgroundColor: '#f0f0f0', borderWidth: 1, borderColor: '#e0e0e0',
    borderRadius: 8, paddingVertical: 8, paddingHorizontal: 10,
  },
  manualLabel: { fontSize: 12, fontWeight: '600', color: '#444' },
  manualInput: {
    borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 8,
    paddingVertical: 8, paddingHorizontal: 10, fontSize: 15, backgroundColor: '#fff',
  },
  checkBtn: {
    marginTop: 4, borderRadius: 8, borderWidth: 2, borderColor: '#ccc',
    paddingVertical: 10, alignItems: 'center',
  },
  checkBtnDone: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  checkBtnText: { fontSize: 14, fontWeight: '700', color: '#555' },
  checkBtnTextDone: { color: '#fff' },
});
