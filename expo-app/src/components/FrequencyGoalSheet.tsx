import React, { useEffect, useState } from 'react';
import { Modal, View, Text, StyleSheet, TouchableOpacity, Pressable, ActivityIndicator } from 'react-native';
import type { MobileFrequencyGoalResponse } from '../api/types';

export const MIN_TARGET = 1;
export const MAX_TARGET = 7;
const DEFAULT_TARGET = 3;

type Props = {
  visible: boolean;
  goal: MobileFrequencyGoalResponse | null;
  onClose: () => void;
  /** 保存（1〜7回）。失敗時はエラー文言をthrowする */
  onSave: (weeklyTargetCount: number) => Promise<void>;
};

/**
 * 「週の回数目標」の設定シート（機能見直し-1-#7、仮画面パターンF・G）。下から開き、1〜7回を「−」「＋」で選ぶ。
 * 期分けプログラムの実施中は表示のみ（変更不可）で、手入力の目標はプログラム終了後の週から使われる旨を案内する。
 * #1の教訓どおり、このModalの上に別のModalは重ねない（エラーもシート内に表示する）。
 */
export default function FrequencyGoalSheet({ visible, goal, onClose, onSave }: Props) {
  const [value, setValue] = useState(DEFAULT_TARGET);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const readOnly = !!goal?.programActive;

  useEffect(() => {
    if (visible) {
      setValue(goal?.manualTarget ?? DEFAULT_TARGET);
      setError(null);
      setSaving(false);
    }
  }, [visible, goal]);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave(value);
    } catch (e: any) {
      setError(typeof e?.message === 'string' && e.message ? e.message : '保存できませんでした');
      setSaving(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} testID="frequency-goal-sheet-backdrop">
        <Pressable style={styles.sheet} onPress={() => {}}>
          <Text style={styles.title}>週の回数目標</Text>
          {readOnly ? (
            <>
              <Text style={styles.text}>{goal?.programSheetLabel}</Text>
              <View style={styles.stepper}>
                <View style={[styles.stepButton, styles.stepButtonDisabled]}>
                  <Text style={[styles.stepButtonText, styles.stepButtonTextDisabled]}>−</Text>
                </View>
                {goal?.manualTarget != null ? (
                  <Text style={[styles.value, styles.valueDisabled]}>
                    {goal.manualTarget}<Text style={styles.valueUnit}>回</Text>
                  </Text>
                ) : (
                  <Text style={[styles.value, styles.valueDisabled, styles.unset]}>未設定</Text>
                )}
                <View style={[styles.stepButton, styles.stepButtonDisabled]}>
                  <Text style={[styles.stepButtonText, styles.stepButtonTextDisabled]}>＋</Text>
                </View>
              </View>
              <Text style={styles.text}>ここで決めた回数は、プログラムが終わった後の週から使われます。</Text>
              <View style={styles.buttons}>
                <TouchableOpacity style={[styles.button, styles.buttonSecondary]} onPress={onClose}>
                  <Text style={styles.buttonSecondaryText}>閉じる</Text>
                </TouchableOpacity>
              </View>
            </>
          ) : (
            <>
              <Text style={styles.text}>
                1週間（月〜日）に何日トレーニングするかを決めます。休養日も計画のうちです。
              </Text>
              <View style={styles.stepper}>
                <TouchableOpacity
                  style={[styles.stepButton, value <= MIN_TARGET && styles.stepButtonDisabled]}
                  onPress={() => setValue((v) => Math.max(MIN_TARGET, v - 1))}
                  disabled={value <= MIN_TARGET || saving}
                  accessibilityLabel="1回減らす"
                  testID="frequency-goal-minus"
                >
                  <Text style={[styles.stepButtonText, value <= MIN_TARGET && styles.stepButtonTextDisabled]}>−</Text>
                </TouchableOpacity>
                <Text style={styles.value} testID="frequency-goal-value">
                  {value}<Text style={styles.valueUnit}>回</Text>
                </Text>
                <TouchableOpacity
                  style={[styles.stepButton, value >= MAX_TARGET && styles.stepButtonDisabled]}
                  onPress={() => setValue((v) => Math.min(MAX_TARGET, v + 1))}
                  disabled={value >= MAX_TARGET || saving}
                  accessibilityLabel="1回増やす"
                  testID="frequency-goal-plus"
                >
                  <Text style={[styles.stepButtonText, value >= MAX_TARGET && styles.stepButtonTextDisabled]}>＋</Text>
                </TouchableOpacity>
              </View>
              <Text style={styles.text}>
                変更は来週の評価から反映されます。{'\n'}これまでの週の結果は変わりません。
              </Text>
              {error && <Text style={styles.error}>{error}</Text>}
              <View style={styles.buttons}>
                <TouchableOpacity style={[styles.button, styles.buttonSecondary]} onPress={onClose} disabled={saving}>
                  <Text style={styles.buttonSecondaryText}>キャンセル</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[styles.button, styles.buttonPrimary]}
                  onPress={handleSave}
                  disabled={saving}
                  testID="frequency-goal-save"
                >
                  {saving ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Text style={styles.buttonPrimaryText}>保存する</Text>
                  )}
                </TouchableOpacity>
              </View>
            </>
          )}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: '#fff', borderTopLeftRadius: 16, borderTopRightRadius: 16,
    paddingHorizontal: 18, paddingTop: 18, paddingBottom: 34, gap: 14,
  },
  title: { fontSize: 17, fontWeight: '800', color: '#222' },
  text: { fontSize: 13, color: '#666', lineHeight: 20 },
  stepper: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 20 },
  stepButton: {
    width: 46, height: 46, borderRadius: 23, borderWidth: 1.5, borderColor: '#4CAF50',
    alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff',
  },
  stepButtonDisabled: { borderColor: '#ccc' },
  stepButtonText: { fontSize: 22, fontWeight: '700', color: '#4CAF50', lineHeight: 26 },
  stepButtonTextDisabled: { color: '#ccc' },
  value: { fontSize: 30, fontWeight: '800', color: '#222', minWidth: 100, textAlign: 'center' },
  valueDisabled: { color: '#aaa' },
  valueUnit: { fontSize: 15, fontWeight: '700' },
  unset: { fontSize: 18 },
  error: { fontSize: 12.5, color: '#ef4444' },
  buttons: { flexDirection: 'row', gap: 10 },
  button: { flex: 1, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },
  buttonPrimary: { backgroundColor: '#4CAF50' },
  buttonPrimaryText: { color: '#fff', fontSize: 14, fontWeight: '700' },
  buttonSecondary: { backgroundColor: '#f0f0f0' },
  buttonSecondaryText: { color: '#666', fontSize: 14, fontWeight: '700' },
});
