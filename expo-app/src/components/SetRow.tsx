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
  onCompleted?: (recommendedIntervalSeconds?: number) => void;
  onUncompleted?: () => void;
  onDelete?: () => void;
  canDelete?: boolean;
}

const SET_TYPE_LABEL: Record<string, string> = {
  WARMUP: 'W', MAIN: 'M', DROPSET: 'D',
};

/** 機能見直し-1-#18: RIR（余力）の選択肢。5は「5以上」 */
const RIR_OPTIONS = [0, 1, 2, 3, 4, 5];

export function formatRirOption(rir: number): string {
  return rir >= 5 ? '5以上' : String(rir);
}

export function formatRirLabel(rir: number): string {
  return rir >= 5 ? 'あと5回以上' : `あと${rir}回`;
}

/**
 * 選択欄の開き方。
 * - afterComplete: 完了チェック直後。「スキップ」で何も記録せずに閉じる
 * - edit: 完了済みセットの表示を押して開き直したとき。「記録しない」で未入力に戻す
 */
type RirPickerMode = 'afterComplete' | 'edit' | null;

export default function SetRow({
  detail, onUpdated, onCompleted, onUncompleted, onDelete, canDelete,
}: Props) {
  const [weight, setWeight] = useState(String(detail.weight));
  const [reps, setReps] = useState(String(detail.reps));
  const [loading, setLoading] = useState(false);
  const [completedLocal, setCompletedLocal] = useState(detail.completed);
  const [rirLocal, setRirLocal] = useState<number | null>(detail.rir ?? null);
  const [rirPicker, setRirPicker] = useState<RirPickerMode>(null);
  const editingRef = useRef(false);
  // RIRは本番セットだけ。setType未設定の古いデータはサーバー側の既定（MAIN）に合わせる
  const isMainSet = (detail.setType ?? 'MAIN') === 'MAIN';

  useEffect(() => {
    if (!editingRef.current) {
      setWeight(String(detail.weight));
      setReps(String(detail.reps));
    }
  }, [detail.weight, detail.reps]);

  useEffect(() => {
    setCompletedLocal(detail.completed);
  }, [detail.completed]);

  useEffect(() => {
    setRirLocal(detail.rir ?? null);
  }, [detail.rir]);

  async function handleToggleComplete() {
    const newCompleted = !completedLocal;
    const w = parseFloat(weight) || 0;
    const r = parseInt(reps, 10) || 0;

    setCompletedLocal(newCompleted);
    onUpdated({ ...detail, weight: w, reps: r, completed: newCompleted });
    if (!newCompleted) {
      setRirPicker(null);
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

      // F4: 推奨インターバル秒数をサーバーレスポンスから受け取り、インターバルタイマー開始に反映する
      if (newCompleted) {
        onCompleted?.(data.recommendedIntervalSeconds);
        // 機能見直し-1-#18: 本番セットの完了直後に、任意で「あと何回できたか」を選ぶ欄を出す（選ばなくても進める）
        if (isMainSet && data.completed) {
          setRirPicker('afterComplete');
        }
      }

      // バックエンド側で @JsonProperty("PR") によりキー名を "PR" に明示固定している
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

  async function handleSelectRir(rir: number | null) {
    const prev = rirLocal;
    setRirPicker(null);
    if (rir === prev) return;
    setRirLocal(rir);
    onUpdated({ ...detail, rir });
    try {
      await trainingApi.updateSetRir(detail.id, rir);
    } catch (e: any) {
      setRirLocal(prev);
      onUpdated({ ...detail, rir: prev });
      Alert.alert('エラー', e.response?.data?.error ?? '余力の保存に失敗しました');
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
    <View>
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

        {/* 機能見直し-1-#18: RIR（余力）。完了済みの本番セットのみ表示し、押すと選び直せる。
            列の位置がそろうよう、表示しない行でも同じ幅の枠を確保する */}
        <View style={styles.rirSlot}>
          {completedLocal && isMainSet && (
            <TouchableOpacity
              onPress={() => setRirPicker(rirPicker ? null : 'edit')}
              accessibilityLabel="余力を記録"
              hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
            >
              {rirLocal != null ? (
                <Text style={styles.rirText}>{formatRirLabel(rirLocal)}</Text>
              ) : (
                <Text style={styles.rirTextEmpty}>あと?回</Text>
              )}
            </TouchableOpacity>
          )}
        </View>

        {/* 完了チェック */}
        <TouchableOpacity
          style={[styles.check, completedLocal && styles.checkDone]}
          onPress={handleToggleComplete}
          disabled={loading}
          accessibilityLabel="セット完了"
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

      {/* 機能見直し-1-#18: あと何回できたかの選択欄（任意）。完了でうすく表示される行の外に置く */}
      {rirPicker && completedLocal && isMainSet && (
        <View style={styles.rirPicker}>
          <Text style={styles.rirPickerTitle}>あと何回できましたか？（任意）</Text>
          <View style={styles.rirOptions}>
            {RIR_OPTIONS.map((v) => (
              <TouchableOpacity
                key={v}
                style={[styles.rirOption, rirLocal === v && styles.rirOptionSelected]}
                onPress={() => handleSelectRir(v)}
              >
                <Text style={[styles.rirOptionText, rirLocal === v && styles.rirOptionTextSelected]}>
                  {formatRirOption(v)}
                </Text>
              </TouchableOpacity>
            ))}
            {rirPicker === 'afterComplete' ? (
              <TouchableOpacity style={styles.rirSkip} onPress={() => setRirPicker(null)}>
                <Text style={styles.rirSkipText}>スキップ</Text>
              </TouchableOpacity>
            ) : (
              <TouchableOpacity style={styles.rirSkip} onPress={() => handleSelectRir(null)}>
                <Text style={styles.rirSkipText}>記録しない</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
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
  rirSlot: { width: 60, alignItems: 'center' },
  rirText: { fontSize: 12, color: '#2E7D32', fontWeight: '700' },
  rirTextEmpty: { fontSize: 12, color: '#bbb' },
  rirPicker: {
    paddingVertical: 8, paddingHorizontal: 8, backgroundColor: '#F1F8E9',
    borderRadius: 8, marginVertical: 4,
  },
  rirPickerTitle: { fontSize: 12, color: '#555', marginBottom: 6 },
  rirOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' },
  rirOption: {
    minWidth: 36, paddingHorizontal: 8, paddingVertical: 6, borderRadius: 6,
    borderWidth: 1, borderColor: '#C5E1A5', backgroundColor: '#fff', alignItems: 'center',
  },
  rirOptionSelected: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  rirOptionText: { fontSize: 13, color: '#333', fontWeight: '600' },
  rirOptionTextSelected: { color: '#fff' },
  rirSkip: { paddingHorizontal: 8, paddingVertical: 6 },
  rirSkipText: { fontSize: 12, color: '#888' },
});
