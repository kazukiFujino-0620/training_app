import React, { useState, useEffect } from 'react';
import {
  View, Text, FlatList, TextInput, TouchableOpacity,
  StyleSheet, Alert, ActivityIndicator, Modal, ScrollView,
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
  { code: 'ARM', label: '腕' },
  { code: 'LEG', label: '脚' },
];

interface SetConfig {
  weight: string;
  reps: string;
  setType: 'WARMUP' | 'MAIN';
}

/** 選択された種目1件分のトレーニングブロック（種目情報＋セット配列） */
interface TrainingBlock {
  item: TrainingItemMaster;
  sets: SetConfig[];
}

export default function AddExerciseScreen({ navigation }: Props) {
  const [items, setItems]         = useState<TrainingItemMaster[]>([]);
  const [loading, setLoading]     = useState(true);
  const [partCode, setPartCode]   = useState('');
  const [search, setSearch]       = useState('');

  // ── 複数選択状態（種目一覧でチェックされたIDの集合） ────────────────────
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  // ── 一括セット入力画面（モーダル）の状態 ────────────────────────────────
  const [blocksVisible, setBlocksVisible] = useState(false);
  const [blocks, setBlocks] = useState<TrainingBlock[]>([]);
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

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  /** 種目一覧で確定（チェック済み種目をブロック化して一括セット入力画面を開く） */
  function confirmSelection() {
    const selectedItems = items.filter((item) => selectedIds.has(item.id));
    if (selectedItems.length === 0) return;
    setBlocks(selectedItems.map((item) => ({ item, sets: [] })));
    setBlocksVisible(true);
  }

  function closeBlocks() {
    setBlocksVisible(false);
    setBlocks([]);
  }

  function updateSet(blockIndex: number, setIndex: number, field: keyof SetConfig, value: string) {
    setBlocks((prev) => prev.map((b, bi) => {
      if (bi !== blockIndex) return b;
      return {
        ...b,
        sets: b.sets.map((s, si) => (si === setIndex ? { ...s, [field]: value } : s)),
      };
    }));
  }

  /** セット追加。2セット目以降は直前セットの重量・回数・種別を引き継ぐ（0行始まり） */
  function addSet(blockIndex: number) {
    setBlocks((prev) => prev.map((b, bi) => {
      if (bi !== blockIndex) return b;
      const last = b.sets[b.sets.length - 1];
      const newSet: SetConfig = last
        ? { weight: last.weight, reps: last.reps, setType: last.setType }
        : { weight: '', reps: '', setType: 'MAIN' };
      return { ...b, sets: [...b.sets, newSet] };
    }));
  }

  function removeSet(blockIndex: number, setIndex: number) {
    setBlocks((prev) => prev.map((b, bi) => {
      if (bi !== blockIndex) return b;
      return { ...b, sets: b.sets.filter((_, si) => si !== setIndex) };
    }));
  }

  function removeBlock(blockIndex: number) {
    setBlocks((prev) => prev.filter((_, bi) => bi !== blockIndex));
  }

  /** 一括登録。新APIが未整備のため、既存の単一登録APIを種目数分ループ呼び出しする暫定実装。 */
  async function handleRegisterAll() {
    const targets = blocks
      .map((b) => ({
        item: b.item,
        validSets: b.sets.filter((s) => s.weight !== '' && s.reps !== ''),
      }));

    const emptyBlock = targets.find((t) => t.validSets.length === 0);
    if (emptyBlock) {
      Alert.alert(
        '入力エラー',
        `「${emptyBlock.item.itemName}」に重量と回数が入力されたセットがありません。各種目に少なくとも1セット入力してください。`,
      );
      return;
    }

    setSaving(true);
    let successCount = 0;
    let failCount = 0;
    const failedNames: string[] = [];

    for (const t of targets) {
      try {
        await trainingApi.addTraining({
          menu: t.item.itemName,
          partCode: t.item.partCode,
          sets: t.validSets.map((s) => ({
            weight: parseFloat(s.weight) || 0,
            reps: parseInt(s.reps, 10) || 0,
            setType: s.setType,
          })),
        });
        successCount += 1;
      } catch {
        failCount += 1;
        failedNames.push(t.item.itemName);
      }
    }

    setSaving(false);

    const total = targets.length;
    if (failCount === 0) {
      Alert.alert('登録完了', `${total}件中 ${successCount}件登録しました`, [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } else if (successCount === 0) {
      Alert.alert('登録失敗', `${total}件すべての登録に失敗しました`);
    } else {
      Alert.alert(
        '一部登録できませんでした',
        `${total}件中 ${successCount}件登録成功、${failCount}件失敗しました\n\n失敗: ${failedNames.join('、')}`,
        [{ text: 'OK', onPress: () => navigation.goBack() }],
      );
    }
  }

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {/* 部位フィルター（flexWrap によるシンプルな折り返し実装。横スクロールFlatListをやめて
          文字化け・キーボード表示時のレイアウト崩れの根本原因になり得る要因を排除する） */}
      <View style={styles.partRow}>
        {PARTS.map((p) => (
          <TouchableOpacity
            key={p.code}
            style={[styles.partChip, partCode === p.code && styles.partChipActive]}
            onPress={() => setPartCode(p.code)}
          >
            <Text style={[styles.partChipText, partCode === p.code && styles.partChipTextActive]}>
              {p.label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

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
          contentContainerStyle={selectedIds.size > 0 ? styles.listWithFooter : undefined}
          renderItem={({ item }) => {
            const checked = selectedIds.has(item.id);
            return (
              <TouchableOpacity
                style={[styles.item, checked && styles.itemChecked]}
                onPress={() => toggleSelect(item.id)}
                activeOpacity={0.7}
              >
                <View style={[styles.checkbox, checked && styles.checkboxChecked]}>
                  {checked && <Text style={styles.checkboxMark}>✓</Text>}
                </View>
                <Text style={styles.itemName}>{item.itemName}</Text>
              </TouchableOpacity>
            );
          }}
          ListEmptyComponent={
            <Text style={styles.emptyText}>種目が見つかりません</Text>
          }
        />
      )}

      {/* 確定ボタン（1件以上選択時のみ表示） */}
      {selectedIds.size > 0 && (
        <View style={styles.confirmBar}>
          <TouchableOpacity style={styles.confirmBtn} onPress={confirmSelection}>
            <Text style={styles.confirmBtnText}>
              次へ（{selectedIds.size}件選択中）
            </Text>
          </TouchableOpacity>
        </View>
      )}

      {/* 一括セット入力モーダル（複数種目分のトレーニングブロックをまとめて表示） */}
      <Modal visible={blocksVisible} animationType="slide" presentationStyle="pageSheet">
        <SafeAreaView style={styles.modal}>
          <View style={styles.modalHeader}>
            <TouchableOpacity style={styles.headerBtnLeft} onPress={closeBlocks}>
              <Text style={styles.cancelText}>キャンセル</Text>
            </TouchableOpacity>
            <Text style={styles.modalTitle} numberOfLines={1} ellipsizeMode="tail">
              {blocks.length}種目を登録
            </Text>
            <TouchableOpacity style={styles.headerBtnRight} onPress={handleRegisterAll} disabled={saving}>
              {saving
                ? <ActivityIndicator color="#4CAF50" />
                : <Text style={styles.saveText}>登録</Text>}
            </TouchableOpacity>
          </View>

          <ScrollView contentContainerStyle={styles.blocksScroll} keyboardShouldPersistTaps="handled">
            {blocks.map((block, blockIndex) => (
              <View key={block.item.id} style={styles.block}>
                <View style={styles.blockHeader}>
                  <Text style={styles.blockTitle} numberOfLines={1} ellipsizeMode="tail">
                    {block.item.itemName}
                  </Text>
                  <TouchableOpacity onPress={() => removeBlock(blockIndex)}>
                    <Text style={styles.blockRemoveText}>✕</Text>
                  </TouchableOpacity>
                </View>

                {block.sets.length === 0 && (
                  <Text style={styles.noSetText}>「セット追加」で記録を開始してください</Text>
                )}

                {block.sets.map((s, setIndex) => (
                  <View key={setIndex} style={styles.setRow}>
                    <Text style={styles.setNum}>{setIndex + 1}</Text>
                    <TouchableOpacity
                      style={[styles.typeBtn, s.setType === 'WARMUP' && styles.typeBtnActive]}
                      onPress={() => updateSet(
                        blockIndex, setIndex, 'setType',
                        s.setType === 'WARMUP' ? 'MAIN' : 'WARMUP',
                      )}
                    >
                      <Text style={styles.typeBtnText}>{s.setType === 'WARMUP' ? 'W' : 'M'}</Text>
                    </TouchableOpacity>
                    <TextInput
                      style={styles.setInput}
                      placeholder="重量"
                      value={s.weight}
                      onChangeText={(v) => updateSet(blockIndex, setIndex, 'weight', v)}
                      keyboardType="decimal-pad"
                    />
                    <Text style={styles.unit}>kg</Text>
                    <TextInput
                      style={styles.setInput}
                      placeholder="回数"
                      value={s.reps}
                      onChangeText={(v) => updateSet(blockIndex, setIndex, 'reps', v)}
                      keyboardType="number-pad"
                    />
                    <Text style={styles.unit}>回</Text>
                    <TouchableOpacity onPress={() => removeSet(blockIndex, setIndex)}>
                      <Text style={styles.removeText}>✕</Text>
                    </TouchableOpacity>
                  </View>
                ))}

                <TouchableOpacity style={styles.addSetBtn} onPress={() => addSet(blockIndex)}>
                  <Text style={styles.addSetText}>＋ セット追加</Text>
                </TouchableOpacity>
              </View>
            ))}

            {blocks.length === 0 && (
              <Text style={styles.emptyText}>種目が選択されていません</Text>
            )}
          </ScrollView>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  // 部位フィルター: flexWrap を使った View ベースの折り返し実装
  partRow: {
    flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center',
    paddingHorizontal: 12, paddingVertical: 10, gap: 8,
  },
  partChip: {
    paddingHorizontal: 14, paddingVertical: 6, borderRadius: 16, minHeight: 32,
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#e0e0e0',
    alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  },
  partChipActive: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  partChipText: { fontSize: 13, color: '#555' },
  partChipTextActive: { color: '#fff', fontWeight: '700' },
  search: {
    marginHorizontal: 16, marginBottom: 8, backgroundColor: '#fff',
    borderRadius: 10, padding: 12, fontSize: 15, borderWidth: 1, borderColor: '#e0e0e0',
  },
  listWithFooter: { paddingBottom: 84 },
  item: {
    flexDirection: 'row', alignItems: 'center', gap: 12,
    backgroundColor: '#fff', marginHorizontal: 16, marginVertical: 4,
    padding: 16, borderRadius: 10, borderWidth: 1, borderColor: '#eee',
  },
  itemChecked: { borderColor: '#4CAF50', backgroundColor: '#F1F8F1' },
  checkbox: {
    width: 22, height: 22, borderRadius: 5, borderWidth: 2, borderColor: '#ccc',
    alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  },
  checkboxChecked: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  checkboxMark: { color: '#fff', fontSize: 14, fontWeight: '700' },
  itemName: { fontSize: 16, color: '#222', flex: 1, flexShrink: 1 },
  emptyText: { textAlign: 'center', color: '#aaa', padding: 32 },
  // 確定ボタンバー
  confirmBar: {
    position: 'absolute', left: 0, right: 0, bottom: 0,
    padding: 16, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#eee',
  },
  confirmBtn: {
    backgroundColor: '#4CAF50', borderRadius: 10, paddingVertical: 14,
    alignItems: 'center', justifyContent: 'center',
  },
  confirmBtnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  // modal
  modal: { flex: 1, backgroundColor: '#f5f5f5' },
  modalHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    padding: 16, backgroundColor: '#fff', borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  headerBtnLeft: { flexShrink: 0 },
  headerBtnRight: { flexShrink: 0 },
  cancelText: { color: '#999', fontSize: 15 },
  modalTitle: { flex: 1, fontSize: 16, fontWeight: '700', color: '#222', paddingHorizontal: 8, textAlign: 'center' },
  saveText: { color: '#4CAF50', fontSize: 15, fontWeight: '700' },
  blocksScroll: { paddingBottom: 32 },
  block: {
    backgroundColor: '#fff', marginHorizontal: 16, marginTop: 12,
    borderRadius: 12, borderWidth: 1, borderColor: '#eee', padding: 12,
  },
  blockHeader: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginBottom: 8,
  },
  blockTitle: { flex: 1, fontSize: 16, fontWeight: '700', color: '#222', paddingRight: 8 },
  blockRemoveText: { fontSize: 16, color: '#ccc', paddingHorizontal: 4, flexShrink: 0 },
  noSetText: { color: '#aaa', fontSize: 13, paddingVertical: 8 },
  setRow: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    backgroundColor: '#fafafa', marginTop: 8,
    padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#eee',
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
    padding: 8, fontSize: 15, textAlign: 'center', backgroundColor: '#fff',
  },
  unit: { fontSize: 12, color: '#888' },
  removeText: { fontSize: 16, color: '#ccc', paddingHorizontal: 4 },
  addSetBtn: {
    marginTop: 10, padding: 12,
    borderRadius: 8, borderWidth: 1, borderColor: '#e0e0e0',
    alignItems: 'center', backgroundColor: '#fff',
  },
  addSetText: { color: '#4CAF50', fontSize: 14, fontWeight: '600' },
});
