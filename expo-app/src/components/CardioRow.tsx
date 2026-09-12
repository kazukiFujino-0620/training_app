import React, { useState, useEffect } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { TrainingDetail } from '../api/types';
import { trainingApi } from '../api/client';

interface Props {
  detail: TrainingDetail;
  onUpdated: (updated: TrainingDetail) => void;
}

/**
 * 有酸素運動（ita2-1）の記録行。セット概念が無いため、SetRowとは別に
 * 実施時間・距離/平均心拍数/消費カロリーを表示する。
 *
 * 実施時間は種目カードごとに個別計測する（開始ボタンを押した時刻からの経過時間）。
 * セッション全体のタイマーを流用すると、1セッション内で複数の有酸素種目を行った場合に
 * 2種目目以降の記録が「その種目単体の時間」ではなく「セッション開始からの累積時間」に
 * なってしまうため使わない（2026-08-19 ユーザー確認の上、個別タイマー方式に変更）。
 */
export default function CardioRow({ detail, onUpdated }: Props) {
  const [distance, setDistance] = useState(detail.distanceKm != null ? String(detail.distanceKm) : '');
  const [heartRate, setHeartRate] = useState(detail.avgHeartRateBpm != null ? String(detail.avgHeartRateBpm) : '');
  const [calories, setCalories] = useState(detail.caloriesKcal != null ? String(detail.caloriesKcal) : '');
  const [loading, setLoading] = useState(false);
  const [completedLocal, setCompletedLocal] = useState(detail.completed);
  const [durationMinLocal, setDurationMinLocal] = useState(detail.durationMin);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsedSec, setElapsedSec] = useState(0);

  useEffect(() => {
    if (startedAt === null || completedLocal) return;
    const id = setInterval(() => {
      setElapsedSec(Math.floor((Date.now() - startedAt) / 1000));
    }, 1000);
    return () => clearInterval(id);
  }, [startedAt, completedLocal]);

  function handleStart() {
    setStartedAt(Date.now());
    setElapsedSec(0);
  }

  async function handleComplete() {
    setLoading(true);
    try {
      const nextDurationMin =
        startedAt != null ? Math.max(0, Math.round((Date.now() - startedAt) / 60000)) : 0;
      const payload: {
        isCompleted: boolean;
        durationMin?: number;
        distanceKm?: number;
        avgHeartRateBpm?: number;
        caloriesKcal?: number;
      } = { isCompleted: true, durationMin: nextDurationMin };
      if (distance !== '') payload.distanceKm = parseFloat(distance);
      if (heartRate !== '') payload.avgHeartRateBpm = parseInt(heartRate, 10);
      if (calories !== '') payload.caloriesKcal = parseFloat(calories);

      const { data } = await trainingApi.updateSet(detail.id, payload);
      setCompletedLocal(data.completed);
      setDurationMinLocal(nextDurationMin);
      onUpdated({
        ...detail,
        completed: data.completed,
        durationMin: nextDurationMin,
        distanceKm: distance !== '' ? parseFloat(distance) : detail.distanceKm,
        avgHeartRateBpm: heartRate !== '' ? parseInt(heartRate, 10) : detail.avgHeartRateBpm,
        caloriesKcal: calories !== '' ? parseFloat(calories) : detail.caloriesKcal,
      });
    } catch {
      Alert.alert('エラー', '更新に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  async function handleUncomplete() {
    setLoading(true);
    try {
      const { data } = await trainingApi.updateSet(detail.id, { isCompleted: false });
      setCompletedLocal(data.completed);
      setStartedAt(null);
      setElapsedSec(0);
      onUpdated({ ...detail, completed: data.completed });
    } catch {
      Alert.alert('エラー', '更新に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  const minutes = String(Math.floor(elapsedSec / 60)).padStart(2, '0');
  const seconds = String(elapsedSec % 60).padStart(2, '0');

  return (
    <View style={styles.container}>
      <View style={styles.field}>
        <View style={styles.labelRow}>
          <Ionicons name="lock-closed-outline" size={12} color="#888" />
          <Text style={styles.autoLabel}>実施時間（分）— 開始ボタンからの経過時間を自動反映</Text>
        </View>
        <Text style={styles.autoValue}>
          {completedLocal && durationMinLocal != null
            ? `${durationMinLocal} 分`
            : startedAt != null
              ? `計測中... ${minutes}:${seconds}`
              : '未計測（下の開始ボタンを押してください）'}
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

      {!completedLocal && startedAt === null && (
        <TouchableOpacity style={styles.checkBtn} onPress={handleStart} disabled={loading}>
          <Text style={styles.checkBtnText}>開始</Text>
        </TouchableOpacity>
      )}
      {!completedLocal && startedAt !== null && (
        <TouchableOpacity style={styles.checkBtn} onPress={handleComplete} disabled={loading}>
          <Text style={styles.checkBtnText}>完了</Text>
        </TouchableOpacity>
      )}
      {completedLocal && (
        <TouchableOpacity
          style={[styles.checkBtn, styles.checkBtnDone]}
          onPress={handleUncomplete}
          disabled={loading}
        >
          <Text style={[styles.checkBtnText, styles.checkBtnTextDone]}>✓ 完了済み</Text>
        </TouchableOpacity>
      )}
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
