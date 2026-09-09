import React, { useCallback, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, FlatList,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { bodyMeasurementApi } from '../api/client';
import type { BodyMeasurement } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'BodyMeasurement'>;
};

function todayDateString(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function formatDate(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`);
  return d.toLocaleDateString('ja-JP', { year: 'numeric', month: 'long', day: 'numeric' });
}

/**
 * 体重・体脂肪率の手動記録画面（ita7-1 1-2）。
 * HealthKit自動同期結果を見る`HealthScreen`とは独立した画面。
 */
export default function BodyMeasurementScreen({ navigation }: Props) {
  const [measurements, setMeasurements] = useState<BodyMeasurement[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [measuredDate, setMeasuredDate] = useState(todayDateString());
  const [weightKg, setWeightKg] = useState('');
  const [bodyFatPct, setBodyFatPct] = useState('');
  const [memo, setMemo] = useState('');

  const load = useCallback(async () => {
    try {
      const { data } = await bodyMeasurementApi.getAll();
      const sorted = [...data].sort((a, b) => (a.measuredDate < b.measuredDate ? 1 : -1));
      setMeasurements(sorted);
    } catch {
      Alert.alert('エラー', '記録の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  async function handleSave() {
    const weight = Number(weightKg);
    if (!weightKg || Number.isNaN(weight)) {
      Alert.alert('入力エラー', '体重を入力してください');
      return;
    }
    if (weight < 20 || weight > 300) {
      Alert.alert('入力エラー', '体重は20〜300kgの範囲で入力してください');
      return;
    }
    let bodyFat: number | undefined;
    if (bodyFatPct.trim() !== '') {
      bodyFat = Number(bodyFatPct);
      if (Number.isNaN(bodyFat) || bodyFat < 0 || bodyFat > 60) {
        Alert.alert('入力エラー', '体脂肪率は0〜60%の範囲で入力してください');
        return;
      }
    }
    if (measuredDate > todayDateString()) {
      Alert.alert('入力エラー', '日付は今日以前を指定してください');
      return;
    }

    setSaving(true);
    try {
      await bodyMeasurementApi.save({
        measuredDate,
        weightKg: weight,
        bodyFatPct: bodyFat,
        memo: memo.trim() || undefined,
      });
      setWeightKg('');
      setBodyFatPct('');
      setMemo('');
      await load();
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.error ?? '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  }

  function handleDelete(id: number) {
    Alert.alert('記録を削除', 'この記録を削除しますか？', [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '削除', style: 'destructive',
        onPress: async () => {
          try {
            await bodyMeasurementApi.delete(id);
            setMeasurements((prev) => prev.filter((m) => m.id !== id));
          } catch {
            Alert.alert('エラー', '削除に失敗しました');
          }
        },
      },
    ]);
  }

  if (loading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}>
          <ActivityIndicator size="large" color="#4CAF50" />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Feather name="chevron-left" size={26} color="#222" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>体重記録</Text>
        <View style={{ width: 32 }} />
      </View>

      <FlatList
        data={measurements}
        keyExtractor={(item) => String(item.id)}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <View style={styles.form}>
            <Text style={styles.fieldLabel}>日付</Text>
            <TextInput
              style={styles.input}
              value={measuredDate}
              onChangeText={setMeasuredDate}
              placeholder="YYYY-MM-DD"
              autoCapitalize="none"
            />

            <Text style={styles.fieldLabel}>体重（kg）</Text>
            <TextInput
              style={styles.input}
              value={weightKg}
              onChangeText={setWeightKg}
              placeholder="例: 65.5"
              keyboardType="decimal-pad"
            />

            <Text style={styles.fieldLabel}>体脂肪率（%・任意）</Text>
            <TextInput
              style={styles.input}
              value={bodyFatPct}
              onChangeText={setBodyFatPct}
              placeholder="例: 18.0"
              keyboardType="decimal-pad"
            />

            <Text style={styles.fieldLabel}>メモ（任意）</Text>
            <TextInput
              style={[styles.input, styles.textarea]}
              value={memo}
              onChangeText={setMemo}
              placeholder="メモ"
              multiline
              numberOfLines={2}
              maxLength={200}
            />

            <TouchableOpacity
              style={[styles.saveButton, saving && styles.saveButtonDisabled]}
              onPress={handleSave}
              disabled={saving}
            >
              {saving ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <Text style={styles.saveButtonText}>記録する</Text>
              )}
            </TouchableOpacity>

            <Text style={styles.historyTitle}>記録履歴</Text>
          </View>
        }
        renderItem={({ item }) => (
          <View style={styles.row}>
            <View style={{ flex: 1 }}>
              <Text style={styles.rowDate}>{formatDate(item.measuredDate)}</Text>
              <Text style={styles.rowValue}>
                {item.weightKg} kg
                {item.bodyFatPct != null ? `　体脂肪率 ${item.bodyFatPct}%` : ''}
              </Text>
              {!!item.memo && <Text style={styles.rowMemo}>{item.memo}</Text>}
              <Text style={styles.rowSource}>{item.source === 'MANUAL' ? '手動記録' : 'ヘルスケア同期'}</Text>
            </View>
            <TouchableOpacity onPress={() => handleDelete(item.id)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Feather name="trash-2" size={18} color="#e53935" />
            </TouchableOpacity>
          </View>
        )}
        ListEmptyComponent={
          <Text style={styles.emptyText}>まだ記録がありません</Text>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 8, paddingVertical: 12, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backButton: { padding: 6 },
  headerTitle: { fontSize: 17, fontWeight: '800', color: '#222' },
  list: { padding: 16, paddingBottom: 40 },
  form: {
    backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 20,
  },
  fieldLabel: { fontSize: 13, fontWeight: '700', color: '#333', marginTop: 12, marginBottom: 6 },
  input: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10,
    fontSize: 15, color: '#222', backgroundColor: '#fff',
  },
  textarea: { minHeight: 60, textAlignVertical: 'top' },
  saveButton: {
    marginTop: 18, backgroundColor: '#4CAF50', borderRadius: 10,
    paddingVertical: 14, alignItems: 'center',
  },
  saveButtonDisabled: { opacity: 0.6 },
  saveButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  historyTitle: { fontSize: 15, fontWeight: '800', color: '#222', marginTop: 4 },
  row: {
    flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between',
    backgroundColor: '#fff', borderRadius: 12, padding: 14, marginBottom: 10,
  },
  rowDate: { fontSize: 12, color: '#999', marginBottom: 4 },
  rowValue: { fontSize: 16, fontWeight: '700', color: '#222' },
  rowMemo: { fontSize: 12, color: '#666', marginTop: 4 },
  rowSource: { fontSize: 11, color: '#aaa', marginTop: 4 },
  emptyText: { textAlign: 'center', color: '#999', fontSize: 14, marginTop: 24 },
});
