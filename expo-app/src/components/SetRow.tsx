import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, Alert,
} from 'react-native';
import type { TrainingDetail, SetUpdateResponse } from '../api/types';
import { trainingApi } from '../api/client';

interface Props {
  detail: TrainingDetail;
  onUpdated: (updated: TrainingDetail) => void;
}

const SET_TYPE_LABEL: Record<string, string> = {
  WARMUP: 'W', MAIN: 'M', DROPSET: 'D',
};

export default function SetRow({ detail, onUpdated }: Props) {
  const [weight, setWeight] = useState(String(detail.weight));
  const [reps, setReps] = useState(String(detail.reps));
  const [loading, setLoading] = useState(false);

  async function handleToggleComplete() {
    setLoading(true);
    try {
      const w = parseFloat(weight) || 0;
      const r = parseInt(reps, 10) || 0;
      const { data } = await trainingApi.updateSet(detail.id, {
        weight: w,
        reps: r,
        isCompleted: !detail.completed,
      });
      onUpdated({ ...detail, weight: w, reps: r, completed: data.isCompleted });

      if (data.isPR && data.prMessage) {
        Alert.alert('🏆 新記録！', data.prMessage);
      }
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.message ?? 'セット更新に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  async function handleBlurUpdate() {
    if (detail.completed) return;
    const w = parseFloat(weight);
    const r = parseInt(reps, 10);
    if (isNaN(w) || isNaN(r)) return;
    if (w === detail.weight && r === detail.reps) return;
    try {
      await trainingApi.updateSet(detail.id, { weight: w, reps: r });
      onUpdated({ ...detail, weight: w, reps: r });
    } catch {
      // silently ignore background save errors
    }
  }

  return (
    <View style={[styles.row, detail.completed && styles.rowDone]}>
      {/* セット番号 + タイプバッジ */}
      <View style={styles.setInfo}>
        <Text style={styles.setNum}>{detail.setNumber}</Text>
        <Text style={styles.setType}>{SET_TYPE_LABEL[detail.setType] ?? detail.setType}</Text>
      </View>

      {/* 重量 */}
      <View style={styles.inputGroup}>
        <TextInput
          style={styles.input}
          value={weight}
          onChangeText={setWeight}
          onBlur={handleBlurUpdate}
          keyboardType="decimal-pad"
          editable={!detail.completed}
          selectTextOnFocus
        />
        <Text style={styles.unit}>kg</Text>
      </View>

      {/* 回数 */}
      <View style={styles.inputGroup}>
        <TextInput
          style={styles.input}
          value={reps}
          onChangeText={setReps}
          onBlur={handleBlurUpdate}
          keyboardType="number-pad"
          editable={!detail.completed}
          selectTextOnFocus
        />
        <Text style={styles.unit}>回</Text>
      </View>

      {/* 完了チェック */}
      <TouchableOpacity
        style={[styles.check, detail.completed && styles.checkDone]}
        onPress={handleToggleComplete}
        disabled={loading}
      >
        <Text style={styles.checkText}>{detail.completed ? '✓' : ''}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 4,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
    gap: 8,
  },
  rowDone: { opacity: 0.5 },
  setInfo: { flexDirection: 'row', alignItems: 'center', width: 48, gap: 4 },
  setNum: { fontSize: 15, fontWeight: '700', color: '#333', width: 20, textAlign: 'center' },
  setType: {
    fontSize: 10,
    color: '#fff',
    backgroundColor: '#90CAF9',
    paddingHorizontal: 4,
    paddingVertical: 2,
    borderRadius: 4,
    fontWeight: '700',
  },
  inputGroup: { flexDirection: 'row', alignItems: 'center', gap: 2, flex: 1 },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#e0e0e0',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 6,
    fontSize: 15,
    textAlign: 'center',
    backgroundColor: '#fafafa',
  },
  unit: { fontSize: 12, color: '#888', width: 16 },
  check: {
    width: 32,
    height: 32,
    borderRadius: 16,
    borderWidth: 2,
    borderColor: '#ccc',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkDone: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  checkText: { color: '#fff', fontWeight: '700', fontSize: 14 },
});
