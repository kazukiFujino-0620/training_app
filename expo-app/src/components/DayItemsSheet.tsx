import React, { useEffect, useMemo, useState } from 'react';
import {
  Modal, View, Text, TouchableOpacity, Pressable, StyleSheet, TextInput, ScrollView, ActivityIndicator,
  Keyboard,
} from 'react-native';
import { masterApi } from '../api/client';
import type { PeriodizationItemInput, TrainingItemMaster } from '../api/types';
import { PARTS, errorMessage } from './PeriodizationShared';

/**
 * 曜日別の種目編集シート（機能見直し-1-#3 モックアップの03・06のモバイル版）。
 *
 * #1（PR#265）の教訓: pageSheet等のModalの上に別のModalを重ねるとiOSで表示されないため、
 * この1枚のModalの中で部位の選択・種目の追加（候補リスト）・セット数・削除・並べ替えまで完結させ、
 * ピッカー等の別Modalは開かない。
 */
type Props = {
  visible: boolean;
  title: string;
  subTitle: string;
  /** 白紙作成（06）では部位もこのシートで選ぶ。タイムライン（02）では部位は変更しない */
  editablePart: boolean;
  partCode: string | null;
  items: PeriodizationItemInput[];
  onClose: () => void;
  /** 失敗時はErrorを投げる（シート内にメッセージを出して閉じない） */
  onSave: (items: PeriodizationItemInput[], partCode: string | null) => Promise<void>;
};

let masterCache: TrainingItemMaster[] | null = null;

export default function DayItemsSheet({
  visible, title, subTitle, editablePart, partCode, items, onClose, onSave,
}: Props) {
  const [list, setList] = useState<PeriodizationItemInput[]>([]);
  const [part, setPart] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [master, setMaster] = useState<TrainingItemMaster[]>(masterCache ?? []);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 種目名の入力中は、キーボードでシート（入力欄・候補）が隠れないようシートを画面上部まで広げる。
  // Modal内ではKeyboardAvoidingView・キーボードイベントでの調整がシミュレーター上で効かなかったため、
  // 確実に発火する入力欄のフォーカスで切り替える。
  const [inputFocused, setInputFocused] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setList(items.map((i) => ({ itemName: i.itemName, targetSets: i.targetSets })));
    setPart(partCode);
    setQuery('');
    setError(null);
    if (!masterCache) {
      masterApi.getItems().then(({ data }) => {
        masterCache = data;
        setMaster(data);
      }).catch(() => setError('種目マスタを取得できませんでした'));
    }
    // 開いた時点の内容で初期化する（親の再レンダーで編集中の内容が戻らないよう、visibleの変化時のみ）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const suggestions = useMemo(() => {
    const q = query.trim();
    if (!q) return [];
    return master
      .filter((m) => m.itemName.includes(q) && !list.some((i) => i.itemName === m.itemName))
      // 同じ部位の種目を先に並べる
      .sort((a, b) => (a.partCode === part ? 0 : 1) - (b.partCode === part ? 0 : 1))
      .slice(0, 8);
  }, [query, master, list, part]);

  const changeSets = (idx: number, delta: number) => {
    setList((prev) => prev.map((i, n) => (
      n === idx ? { ...i, targetSets: Math.min(20, Math.max(1, i.targetSets + delta)) } : i
    )));
  };
  const move = (idx: number, delta: number) => {
    setList((prev) => {
      const to = idx + delta;
      if (to < 0 || to >= prev.length) return prev;
      const next = [...prev];
      const [moved] = next.splice(idx, 1);
      next.splice(to, 0, moved);
      return next;
    });
  };

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave(part ? list : [], part);
    } catch (e) {
      setError(errorMessage(e, (e as Error)?.message || '保存に失敗しました'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose}>
        <Pressable style={[styles.sheet, inputFocused && styles.sheetExpanded]} onPress={() => {}}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.sub}>{subTitle}</Text>
          {error && <Text style={styles.error}>{error}</Text>}

          <ScrollView style={inputFocused ? { flex: 1 } : { maxHeight: 420 }} keyboardShouldPersistTaps="handled">
            {editablePart && (
              <View style={styles.chips}>
                {PARTS.map((p) => (
                  <TouchableOpacity
                    key={p.code}
                    style={[styles.chip, part === p.code && styles.chipOn]}
                    onPress={() => setPart(p.code)}
                  >
                    <Text style={[styles.chipText, part === p.code && styles.chipTextOn]}>{p.label}</Text>
                  </TouchableOpacity>
                ))}
                <TouchableOpacity
                  style={[styles.chip, !part && styles.chipOn]}
                  onPress={() => setPart(null)}
                >
                  <Text style={[styles.chipText, !part && styles.chipTextOn]}>休養</Text>
                </TouchableOpacity>
              </View>
            )}

            {part ? (
              <>
                {list.length === 0 && <Text style={styles.empty}>種目がありません。下の欄から追加してください。</Text>}
                {list.map((item, idx) => (
                  <View key={item.itemName} style={styles.row}>
                    <View style={styles.reorder}>
                      <TouchableOpacity onPress={() => move(idx, -1)} disabled={idx === 0} accessibilityLabel={`${item.itemName}を上へ`}>
                        <Text style={[styles.reorderText, idx === 0 && styles.disabled]}>▲</Text>
                      </TouchableOpacity>
                      <TouchableOpacity onPress={() => move(idx, 1)} disabled={idx === list.length - 1} accessibilityLabel={`${item.itemName}を下へ`}>
                        <Text style={[styles.reorderText, idx === list.length - 1 && styles.disabled]}>▼</Text>
                      </TouchableOpacity>
                    </View>
                    <Text style={styles.itemName} numberOfLines={1}>{item.itemName}</Text>
                    <View style={styles.sets}>
                      <TouchableOpacity onPress={() => changeSets(idx, -1)} accessibilityLabel="セット数を減らす">
                        <Text style={styles.setsBtn}>−</Text>
                      </TouchableOpacity>
                      <Text style={styles.setsText}>{item.targetSets}set</Text>
                      <TouchableOpacity onPress={() => changeSets(idx, 1)} accessibilityLabel="セット数を増やす">
                        <Text style={styles.setsBtn}>＋</Text>
                      </TouchableOpacity>
                    </View>
                    <TouchableOpacity
                      onPress={() => setList((prev) => prev.filter((_, n) => n !== idx))}
                      accessibilityLabel={`${item.itemName}を削除`}
                    >
                      <Text style={styles.remove}>×</Text>
                    </TouchableOpacity>
                  </View>
                ))}
                <TextInput
                  style={styles.input}
                  value={query}
                  onChangeText={setQuery}
                  placeholder="＋ 種目を追加（名前を入力）"
                  placeholderTextColor="#aaa"
                  onFocus={() => setInputFocused(true)}
                  onBlur={() => setInputFocused(false)}
                />
                {suggestions.map((s) => (
                  <TouchableOpacity
                    key={s.id}
                    style={styles.suggestion}
                    onPress={() => {
                      setList((prev) => [...prev, { itemName: s.itemName, targetSets: 3 }]);
                      setQuery('');
                      // 追加したらキーボードを閉じてシートを元の高さに戻し、保存ボタンを見えるようにする
                      Keyboard.dismiss();
                    }}
                  >
                    <Text style={styles.suggestionText}>{s.itemName}</Text>
                  </TouchableOpacity>
                ))}
              </>
            ) : (
              <Text style={styles.empty}>休養日です。部位を選ぶと種目を設定できます。</Text>
            )}
          </ScrollView>

          <View style={styles.actions}>
            <TouchableOpacity style={[styles.btn, styles.btnGhost]} onPress={onClose}>
              <Text style={styles.btnGhostText}>キャンセル</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.btn, styles.btnPrimary]} onPress={save} disabled={saving}>
              {saving ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnPrimaryText}>保存する</Text>}
            </TouchableOpacity>
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: '#fff', borderTopLeftRadius: 18, borderTopRightRadius: 18,
    paddingHorizontal: 18, paddingTop: 16, paddingBottom: 28,
  },
  sheetExpanded: { height: '90%' },
  title: { fontSize: 16, fontWeight: '800', color: '#222' },
  sub: { fontSize: 12, color: '#888', marginTop: 2, marginBottom: 10 },
  error: { backgroundColor: '#fde8e8', color: '#b91c1c', padding: 8, borderRadius: 8, fontSize: 12, marginBottom: 8 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 10 },
  chip: { borderWidth: 1, borderColor: '#ddd', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6 },
  chipOn: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  chipText: { fontSize: 12, color: '#666' },
  chipTextOn: { color: '#fff', fontWeight: '700' },
  empty: { fontSize: 13, color: '#999', paddingVertical: 10 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: '#f0f0f0' },
  reorder: { alignItems: 'center' },
  reorderText: { fontSize: 12, color: '#4CAF50', paddingHorizontal: 4 },
  disabled: { color: '#ddd' },
  itemName: { flex: 1, fontSize: 14, fontWeight: '600', color: '#222' },
  sets: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#f5f5f5', borderRadius: 8 },
  setsBtn: { fontSize: 16, color: '#4CAF50', paddingHorizontal: 8, paddingVertical: 2 },
  setsText: { fontSize: 12, color: '#555', minWidth: 34, textAlign: 'center' },
  remove: { fontSize: 18, color: '#e53935', paddingHorizontal: 4 },
  input: {
    marginTop: 12, borderWidth: 1, borderStyle: 'dashed', borderColor: '#ccc', borderRadius: 8,
    paddingHorizontal: 12, paddingVertical: 9, fontSize: 13, color: '#222',
  },
  suggestion: { paddingVertical: 9, paddingHorizontal: 12, borderBottomWidth: 1, borderBottomColor: '#f3f3f3' },
  suggestionText: { fontSize: 13, color: '#333' },
  actions: { flexDirection: 'row', gap: 8, marginTop: 14 },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 12, alignItems: 'center' },
  btnGhost: { borderWidth: 1, borderColor: '#4CAF50' },
  btnGhostText: { color: '#4CAF50', fontWeight: '700' },
  btnPrimary: { backgroundColor: '#4CAF50' },
  btnPrimaryText: { color: '#fff', fontWeight: '700' },
});
