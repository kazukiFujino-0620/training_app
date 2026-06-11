import React, { useState, useEffect } from 'react';
import {
  View, Text, FlatList, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { masterApi, trainingApi } from '../api/client';
import type { TrainingItemMaster } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'AddExercise'>;
};

const PARTS = [
  { code: '', label: 'すべて' },
  { code: 'CHEST', label: '胸' },
  { code: 'BACK', label: '背中' },
  { code: 'SHOULDER', label: '肩' },
  { code: 'BICEPS', label: '上腕二頭筋' },
  { code: 'TRICEPS', label: '上腕三頭筋' },
  { code: 'ABS', label: '腹筋' },
  { code: 'LEG', label: '脚' },
  { code: 'CALVES', label: 'ふくらはぎ' },
];

interface SetConfig {
  weight: string;
  reps: string;
  setType: 'WARMUP' | 'MAIN';
}

export default function AddExerciseScreen({ navigation }: Props) {
  const [items, setItems]         = useState<TrainingItemMaster[]>([]);
  const [loading, setLoading]     = useState(true);
  const [partCode, setPartCode]   = useState('');
  const [search, setSearch]       = useState('');
  const [selected, setSelected]   = useState<TrainingItemMaster | null>(null);
  const [sets, setSets]           = useState<SetConfig[]>([
    { weight: '', reps: '', setType: 'WARMUP' },
    { weight: '', reps: '', setType: 'MAIN' },
    { weight: '', reps: '', setType: 'MAIN' },
  ]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await masterApi.getItems();
        setItems(data);
      } catch {
        Alert.alert('エラー', '種目リストの取得に失敗しました');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const filtered = items.filter((item) => {
    const matchPart = !partCode || item.partCode === partCode;
    const matchSearch = !search || item.itemName.includes(search);
    return matchPart && matchSearch;
  });

  function updateSet(index: number, field: keyof SetConfig, value: string) {
    setSets((prev) => prev.map((s, i) => (i === index ? { ...s, [field]: value } : s)));
  }

  function addSet() {
    setSets((prev) => [...prev, { weight: '', reps: '', setType: 'MAIN' }]);
  }

  function removeSet(index: number) {
    if (sets.length <= 1) return;
    setSets((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleAdd() {
    if (!selected) return;
    const validSets = sets.filter((s) => s.weight !== '' && s.reps !== '');
    if (validSets.length === 0) {
      Alert.alert('入力エラー', '重量と回数を少なくとも1セット入力してください');
      return;
    }
    setSaving(true);
    try {
      await trainingApi.addTraining({
        menu: selected.itemName,
        partCode: selected.partCode,
        sets: validSets.map((s) => ({
          weight: parseFloat(s.weight) || 0,
          reps: parseInt(s.reps, 10) || 0,
          setType: s.setType,
        })),
      });
      Alert.alert('追加完了', `${selected.itemName} を追加しました`, [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.message ?? '追加に失敗しました');
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {/* 部位フィルター */}
      <FlatList
        horizontal
        data={PARTS}
        keyExtractor={(p) => p.code}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={[styles.partChip, partCode === item.code && styles.partChipActive]}
            onPress={() => setPartCode(item.code)}
          >
            <Text style={[styles.partChipText, partCode === item.code && styles.partChipTextActive]}>
              {item.label}
            </Text>
          </TouchableOpacity>
        )}
        contentContainerStyle={styles.partRow}
        showsHorizontalScrollIndicator={false}
      />

      {/* 検索 */}
      <TextInput
        style={styles.search}
        placeholder="種目名で検索..."
        value={search}
        onChangeText={setSearch}
      />

      {loading ? (
        <ActivityIndicator style={{ marginTop: 32 }} size="large" color="#4CAF50" />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <TouchableOpacity style={styles.item} onPress={() => setSelected(item)}>
              <Text style={styles.itemName}>{item.itemName}</Text>
            </TouchableOpacity>
          )}
          ListEmptyComponent={
            <Text style={styles.emptyText}>種目が見つかりません</Text>
          }
        />
      )}

      {/* セット設定モーダル */}
      <Modal visible={!!selected} animationType="slide" presentationStyle="pageSheet">
        <SafeAreaView style={styles.modal}>
          <View style={styles.modalHeader}>
            <TouchableOpacity onPress={() => setSelected(null)}>
              <Text style={styles.cancelText}>キャンセル</Text>
            </TouchableOpacity>
            <Text style={styles.modalTitle}>{selected?.itemName}</Text>
            <TouchableOpacity onPress={handleAdd} disabled={saving}>
              {saving
                ? <ActivityIndicator color="#4CAF50" />
                : <Text style={styles.saveText}>追加</Text>}
            </TouchableOpacity>
          </View>

          <FlatList
            data={sets}
            keyExtractor={(_, i) => String(i)}
            renderItem={({ item: s, index: i }) => (
              <View style={styles.setRow}>
                <Text style={styles.setNum}>{i + 1}</Text>
                <TouchableOpacity
                  style={[styles.typeBtn, s.setType === 'WARMUP' && styles.typeBtnActive]}
                  onPress={() => updateSet(i, 'setType', s.setType === 'WARMUP' ? 'MAIN' : 'WARMUP')}
                >
                  <Text style={styles.typeBtnText}>{s.setType === 'WARMUP' ? 'W' : 'M'}</Text>
                </TouchableOpacity>
                <TextInput
                  style={styles.setInput}
                  placeholder="重量"
                  value={s.weight}
                  onChangeText={(v) => updateSet(i, 'weight', v)}
                  keyboardType="decimal-pad"
                />
                <Text style={styles.unit}>kg</Text>
                <TextInput
                  style={styles.setInput}
                  placeholder="回数"
                  value={s.reps}
                  onChangeText={(v) => updateSet(i, 'reps', v)}
                  keyboardType="number-pad"
                />
                <Text style={styles.unit}>回</Text>
                <TouchableOpacity onPress={() => removeSet(i)}>
                  <Text style={styles.removeText}>✕</Text>
                </TouchableOpacity>
              </View>
            )}
            ListFooterComponent={
              <TouchableOpacity style={styles.addSetBtn} onPress={addSet}>
                <Text style={styles.addSetText}>＋ セット追加</Text>
              </TouchableOpacity>
            }
          />
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  partRow: { paddingHorizontal: 12, paddingVertical: 10, gap: 8 },
  partChip: {
    paddingHorizontal: 14, paddingVertical: 6, borderRadius: 16,
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#e0e0e0',
  },
  partChipActive: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  partChipText: { fontSize: 13, color: '#555' },
  partChipTextActive: { color: '#fff', fontWeight: '700' },
  search: {
    marginHorizontal: 16, marginBottom: 8, backgroundColor: '#fff',
    borderRadius: 10, padding: 12, fontSize: 15, borderWidth: 1, borderColor: '#e0e0e0',
  },
  item: {
    backgroundColor: '#fff', marginHorizontal: 16, marginVertical: 4,
    padding: 16, borderRadius: 10, borderWidth: 1, borderColor: '#eee',
  },
  itemName: { fontSize: 16, color: '#222' },
  emptyText: { textAlign: 'center', color: '#aaa', padding: 32 },
  // modal
  modal: { flex: 1, backgroundColor: '#f5f5f5' },
  modalHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    padding: 16, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  cancelText: { color: '#999', fontSize: 15 },
  modalTitle: { fontSize: 16, fontWeight: '700', color: '#222' },
  saveText: { color: '#4CAF50', fontSize: 15, fontWeight: '700' },
  setRow: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: '#fff', marginHorizontal: 16, marginTop: 8,
    padding: 12, borderRadius: 10, borderWidth: 1, borderColor: '#eee',
  },
  setNum: { fontSize: 14, fontWeight: '700', color: '#555', width: 20 },
  typeBtn: {
    width: 28, height: 28, borderRadius: 14, backgroundColor: '#e0e0e0',
    alignItems: 'center', justifyContent: 'center',
  },
  typeBtnActive: { backgroundColor: '#90CAF9' },
  typeBtnText: { fontSize: 11, fontWeight: '700', color: '#fff' },
  setInput: {
    flex: 1, borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 6,
    padding: 8, fontSize: 15, textAlign: 'center', backgroundColor: '#fafafa',
  },
  unit: { fontSize: 12, color: '#888' },
  removeText: { fontSize: 16, color: '#ccc', paddingHorizontal: 4 },
  addSetBtn: {
    marginHorizontal: 16, marginTop: 12, padding: 14,
    borderRadius: 10, borderWidth: 1, borderColor: '#e0e0e0',
    alignItems: 'center', backgroundColor: '#fff',
  },
  addSetText: { color: '#4CAF50', fontSize: 15, fontWeight: '600' },
});
