import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { TrainingDetail } from '../api/types';
import { trainingApi } from '../api/client';

interface Props {
  detail: TrainingDetail;
  onUpdated: (updated: TrainingDetail) => void;
  onCompleted?: () => void;
  onUncompleted?: () => void;
  onDelete?: () => void;
  canDelete?: boolean;
}

const SET_TYPE_LABEL: Record<string, string> = {
  WARMUP: 'W', MAIN: 'M', DROPSET: 'D',
};

export default function SetRow({
  detail, onUpdated, onCompleted, onUncompleted, onDelete, canDelete,
}: Props) {
  const [weight, setWeight] = useState(String(detail.weight));
  const [reps, setReps] = useState(String(detail.reps));
  const [loading, setLoading] = useState(false);
  const [completedLocal, setCompletedLocal] = useState(detail.completed);
  const editingRef = useRef(false);

  useEffect(() => {
    if (!editingRef.current) {
      setWeight(String(detail.weight));
      setReps(String(detail.reps));
    }
  }, [detail.weight, detail.reps]);

  useEffect(() => {
    setCompletedLocal(detail.completed);
  }, [detail.completed]);

  async function handleToggleComplete() {
    const newCompleted = !completedLocal;
    const w = parseFloat(weight) || 0;
    const r = parseInt(reps, 10) || 0;

    setCompletedLocal(newCompleted);
    onUpdated({ ...detail, weight: w, reps: r, completed: newCompleted });
    if (newCompleted) {
      onCompleted?.();
    } else {
      onUncompleted?.();
    }

    setLoading(true);
    try {
      const { data } = await trainingApi.updateSet(detail.id, {
        weight: w,
        reps: r,
        isCompleted: newCompleted,
      });
      // primitive boolean → isCompleted() getter → Jackson が "is" 剥がして "completed"
      setCompletedLocal(data.completed);
      onUpdated({ ...detail, weight: w, reps: r, completed: data.completed });

      // primitive boolean isPR → isPR() → "PR"（2文字目も大文字なので decapitalize されない）
      if (data.PR && data.prMessage) {
        Alert.alert('新記録！', data.prMessage);
      }
    } catch (e: any) {
      setCompletedLocal(detail.completed);
      onUpdated({ ...detail });
      if (newCompleted) onUncompleted?.();
      Alert.alert('エラー', e.response?.data?.message ?? 'セット更新に失敗しました');
    } finally {
      setLoading(false);
    }
  }

  async function handleBlurUpdate() {
    if (completedLocal) return;
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
    <View style={[styles.row, completedLocal && styles.rowDone]}>
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
          onFocus={() => { editingRef.current = true; }}
          onBlur={() => { editingRef.current = false; handleBlurUpdate(); }}
          keyboardType="decimal-pad"
          editable={!completedLocal}
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
          onFocus={() => { editingRef.current = true; }}
          onBlur={() => { editingRef.current = false; handleBlurUpdate(); }}
          keyboardType="number-pad"
          editable={!completedLocal}
          selectTextOnFocus
        />
        <Text style={styles.unit}>回</Text>
      </View>

      {/* 完了チェック */}
      <TouchableOpacity
        style={[styles.check, completedLocal && styles.checkDone]}
        onPress={handleToggleComplete}
        disabled={loading}
      >
        {completedLocal && <Text style={styles.checkText}>✓</Text>}
      </TouchableOpacity>

      {/* セット削除（2セット以上のときのみ表示） */}
      {canDelete && (
        <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
          <Ionicons name="trash-outline" size={16} color="#aaa" />
        </TouchableOpacity>
      )}
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
    fontSize: 10, color: '#fff', backgroundColor: '#90CAF9',
    paddingHorizontal: 4, paddingVertical: 2, borderRadius: 4, fontWeight: '700',
  },
  inputGroup: { flexDirection: 'row', alignItems: 'center', gap: 2, flex: 1 },
  input: {
    flex: 1, borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 6,
    paddingHorizontal: 8, paddingVertical: 6, fontSize: 15,
    textAlign: 'center', backgroundColor: '#fafafa',
  },
  unit: { fontSize: 12, color: '#888', width: 16 },
  check: {
    width: 32, height: 32, borderRadius: 16, borderWidth: 2, borderColor: '#ccc',
    alignItems: 'center', justifyContent: 'center',
  },
  checkDone: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  checkText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  deleteBtn: { width: 28, height: 28, alignItems: 'center', justifyContent: 'center' },
});
