import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, ActivityIndicator,
  Modal, TextInput, KeyboardAvoidingView, Platform, FlatList,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { masterApi, workoutGenerationApi } from '../api/client';
import type { AutogenExclusion, AutogenForm, AutogenResult, TrainingItemMaster } from '../api/types';
import { excludedSinceLabel, todayString } from '../components/autoWorkoutShared';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'AutoWorkoutGeneration'>;
  route: RouteProp<AppStackParamList, 'AutoWorkoutGeneration'>;
};

/** 使える時間の選択肢（分）。15分以上のみ（最低構成が必ず入る長さ）。 */
export const MINUTE_OPTIONS = [15, 30, 45, 60, 90];
const DEFAULT_MINUTES = 60;

function errorMessage(e: unknown, fallback: string): string {
  const data = (e as { response?: { data?: { error?: string } } })?.response?.data;
  return data?.error ?? fallback;
}

/**
 * 機能見直し-1-#4: メニュー自動作成の条件入力（仮画面A・B・C・C2）。
 * 「メニューを組む」で生成し、再確認の期限が来た除外があれば結果の前に1件ずつ確認する。
 */
export default function AutoWorkoutGenerationScreen({ navigation, route }: Props) {
  const date = route.params?.date;
  const [form, setForm] = useState<AutogenForm | null>(null);
  const [loading, setLoading] = useState(true);
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES);
  const [selectedParts, setSelectedParts] = useState<string[]>([]);
  const [uncheckedEquipment, setUncheckedEquipment] = useState<Set<number>>(new Set());
  const [exclusions, setExclusions] = useState<AutogenExclusion[]>([]);
  const [generating, setGenerating] = useState(false);

  // C: 再確認の待ち行列（先頭を表示）と、回答で解除が出たか
  const [reconfirmQueue, setReconfirmQueue] = useState<AutogenExclusion[]>([]);
  const [pendingResult, setPendingResult] = useState<AutogenResult | null>(null);
  const [releasedAny, setReleasedAny] = useState(false);
  const [answering, setAnswering] = useState(false);

  // C2: 除外の追加
  const [addOpen, setAddOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await workoutGenerationApi.form(date);
      setForm(data);
      setExclusions(data.exclusions);
    } catch (e) {
      Alert.alert('エラー', errorMessage(e, '読み込みに失敗しました'));
    } finally {
      setLoading(false);
    }
  }, [date]);

  useEffect(() => { load(); }, [load]);

  const partName = useMemo(() => {
    const map: Record<string, string> = {};
    form?.parts.forEach((p) => { map[p.partCode] = p.partName; });
    return map;
  }, [form]);

  function togglePart(code: string) {
    setSelectedParts((prev) => (prev.includes(code) ? prev.filter((c) => c !== code) : [...prev, code]));
  }

  function toggleEquipment(id: number) {
    setUncheckedEquipment((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function confirmRemoveExclusion(item: AutogenExclusion) {
    Alert.alert('除外をやめる', `${item.itemName} の除外をやめますか？`, [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: 'やめる',
        style: 'destructive',
        onPress: async () => {
          try {
            await workoutGenerationApi.removeExclusion(item.itemName);
            setExclusions((prev) => prev.filter((e) => e.itemName !== item.itemName));
          } catch (e) {
            Alert.alert('エラー', errorMessage(e, '除外をやめられませんでした'));
          }
        },
      },
    ]);
  }

  async function requestGenerate(): Promise<AutogenResult | null> {
    try {
      const equipmentIds = form && form.equipment.length > 0
        ? form.equipment.filter((e) => !uncheckedEquipment.has(e.id)).map((e) => e.id)
        : undefined;
      const { data } = await workoutGenerationApi.generate({
        availableMinutes: minutes,
        date,
        desiredPartCodes: selectedParts,
        availableEquipmentIds: equipmentIds,
      });
      return data;
    } catch (e) {
      Alert.alert('エラー', errorMessage(e, 'メニューを組めませんでした'));
      return null;
    }
  }

  function showResult(result: AutogenResult) {
    navigation.navigate('AutoWorkoutResult', { result, date });
  }

  async function onGenerate() {
    if (generating) return;
    setGenerating(true);
    const result = await requestGenerate();
    setGenerating(false);
    if (!result) return;
    if (result.itemsNeedingReconfirmation.length > 0) {
      setPendingResult(result);
      setReleasedAny(false);
      setReconfirmQueue(result.itemsNeedingReconfirmation);
      return;
    }
    showResult(result);
  }

  async function answerReconfirm(keep: boolean) {
    const current = reconfirmQueue[0];
    if (!current || answering) return;
    setAnswering(true);
    try {
      await workoutGenerationApi.confirmExclusion(current.itemName, keep);
    } catch (e) {
      setAnswering(false);
      Alert.alert('エラー', errorMessage(e, '回答を保存できませんでした'));
      return;
    }
    const released = releasedAny || !keep;
    if (!keep) setExclusions((prev) => prev.filter((e) => e.itemName !== current.itemName));
    else setExclusions((prev) => prev.map((e) => (e.itemName === current.itemName ? { ...e, needsReconfirmation: false } : e)));
    const rest = reconfirmQueue.slice(1);
    setReconfirmQueue(rest);
    setReleasedAny(released);
    if (rest.length > 0) {
      setAnswering(false);
      return;
    }
    // 全件回答済み: 解除があれば候補が変わるので組み直す
    let result = pendingResult;
    if (released) {
      result = await requestGenerate();
    }
    setAnswering(false);
    setPendingResult(null);
    if (result) showResult({ ...result, itemsNeedingReconfirmation: [] });
  }

  if (loading) {
    return (
      <SafeAreaView style={styles.center} edges={['bottom']}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </SafeAreaView>
    );
  }
  if (!form) {
    return (
      <SafeAreaView style={styles.center} edges={['bottom']}>
        <Text style={styles.muted}>読み込みに失敗しました</Text>
      </SafeAreaView>
    );
  }

  const program = form.program;
  const isToday = !date || date === todayString();
  const programHasPart = !!program?.partCode;
  const current = reconfirmQueue[0];

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <ScrollView contentContainerStyle={styles.body}>
        {program && (
          <View style={styles.prog} testID="autogen-program-banner">
            <Text style={styles.progText}>
              {programBannerText(program, isToday)}
            </Text>
          </View>
        )}

        <View style={styles.card}>
          <Text style={styles.lbl}>使える時間</Text>
          <View style={styles.chips}>
            {MINUTE_OPTIONS.map((m) => (
              <TouchableOpacity
                key={m}
                style={[styles.chip, minutes === m && styles.chipOn]}
                onPress={() => setMinutes(m)}
              >
                <Text style={[styles.chipText, minutes === m && styles.chipTextOn]}>{m}分</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.lbl}>
            {programHasPart ? 'やりたい部位（選ばなければプログラムどおり）' : 'やりたい部位（選ばなければおまかせ）'}
          </Text>
          <View style={styles.chips}>
            {form.parts.map((p) => {
              const on = selectedParts.includes(p.partCode);
              return (
                <TouchableOpacity
                  key={p.partCode}
                  style={[styles.chip, on && styles.chipOn]}
                  onPress={() => togglePart(p.partCode)}
                >
                  <Text style={[styles.chipText, on && styles.chipTextOn]}>{p.partName}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
          {program ? (
            <Text style={styles.hint}>部位を選ぶとそちらを優先し、強度だけプログラムに合わせます</Text>
          ) : selectedParts.length > 1 ? (
            <Text style={styles.hint}>
              選んだ順に優先します（{selectedParts.map((c) => partName[c] ?? c).join(' → ')}）
            </Text>
          ) : null}
        </View>

        {form.equipment.length > 0 && (
          <View style={styles.card}>
            <Text style={styles.lbl}>使える器具</Text>
            <View style={styles.chk}>
              {form.equipment.map((e) => {
                const on = !uncheckedEquipment.has(e.id);
                return (
                  <TouchableOpacity
                    key={e.id}
                    style={styles.chkItem}
                    onPress={() => toggleEquipment(e.id)}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: on }}
                  >
                    <Ionicons name={on ? 'checkbox' : 'square-outline'} size={18} color={on ? '#4CAF50' : '#ccc'} />
                    <Text style={styles.chkText}>{e.name}</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        )}

        <View style={styles.card}>
          <Text style={styles.lbl}>除外している種目</Text>
          {exclusions.map((e) => (
            <View key={e.itemName} style={styles.ex}>
              <View style={{ flex: 1 }}>
                <Text style={styles.exName}>{e.itemName}</Text>
                {e.reason ? <Text style={styles.exSub}>理由: {e.reason}</Text> : null}
              </View>
              <TouchableOpacity
                onPress={() => confirmRemoveExclusion(e)}
                accessibilityLabel="除外をやめる"
                hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              >
                <Text style={styles.exX}>×</Text>
              </TouchableOpacity>
            </View>
          ))}
          <TouchableOpacity onPress={() => setAddOpen(true)}>
            <Text style={styles.addLink}>＋ 除外する種目を追加</Text>
          </TouchableOpacity>
        </View>

        <TouchableOpacity
          style={[styles.btn, styles.btnP, generating && styles.btnDisabled]}
          onPress={onGenerate}
          disabled={generating}
        >
          {generating ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnPText}>メニューを組む</Text>}
        </TouchableOpacity>
      </ScrollView>

      {/* C. 除外の再確認（期限が来たときだけ） */}
      <Modal visible={!!current} transparent animationType="fade" onRequestClose={() => {}}>
        <View style={styles.dim}>
          {current && (
            <View style={styles.sheet}>
              <Text style={styles.sheetTitle}>除外している種目</Text>
              <Text style={styles.sheetText}>この除外設定はまだ必要ですか？</Text>
              <View style={styles.sheetItem}>
                <Text style={styles.sheetItemName}>{current.itemName}</Text>
                {current.excludedSince ? (
                  <Text style={styles.sheetItemSub}>{excludedSinceLabel(current.excludedSince)}</Text>
                ) : null}
              </View>
              <View style={styles.row2}>
                <TouchableOpacity style={[styles.btn, styles.btnG, styles.flex1]} onPress={() => answerReconfirm(false)} disabled={answering}>
                  <Text style={styles.btnGText}>解除する</Text>
                </TouchableOpacity>
                <TouchableOpacity style={[styles.btn, styles.btnP, styles.flex1]} onPress={() => answerReconfirm(true)} disabled={answering}>
                  <Text style={styles.btnPText}>まだ必要</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
        </View>
      </Modal>

      <AddExclusionModal
        visible={addOpen}
        parts={form.parts.map((p) => p.partCode)}
        partName={partName}
        excludedNames={exclusions.map((e) => e.itemName)}
        onClose={() => setAddOpen(false)}
        onAdded={(e) => {
          setExclusions((prev) => [...prev, e]);
          setAddOpen(false);
        }}
      />
    </SafeAreaView>
  );
}

/** B. プログラムの枠の案内文。 */
export function programBannerText(
  program: NonNullable<AutogenForm['program']>,
  isToday: boolean,
): string {
  const day = isToday ? '今日' : 'この日';
  const pct = program.targetIntensityPct != null ? `（目標強度 ${program.targetIntensityPct}%）` : '';
  if (program.partCode) {
    return `${day}はプログラム「${program.cycleName}」第${program.weekNumber}週・${program.partName}の日です${pct}。\n部位を選ばなければ、この予定どおりに組みます。`;
  }
  return `${day}はプログラム「${program.cycleName}」第${program.weekNumber}週の休養日です。\n回復を優先する場合は休むことをおすすめします。部位を選ばなければ軽めに組みます。`;
}

/** C2. 除外の追加（種目を選ぶ → 理由を書いて除外する）。 */
function AddExclusionModal({
  visible, parts, partName, excludedNames, onClose, onAdded,
}: {
  visible: boolean;
  parts: string[];
  partName: Record<string, string>;
  excludedNames: string[];
  onClose: () => void;
  onAdded: (e: AutogenExclusion) => void;
}) {
  const [items, setItems] = useState<TrainingItemMaster[]>([]);
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState<TrainingItemMaster | null>(null);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!visible) return;
    setSelected(null);
    setReason('');
    setFilter('');
    if (items.length > 0) return;
    (async () => {
      try {
        const { data } = await masterApi.getItems();
        setItems(data);
      } catch {
        Alert.alert('エラー', '種目リストの取得に失敗しました');
      }
    })();
  }, [visible, items.length]);

  const candidates = items.filter(
    (i) => parts.includes(i.partCode)
      && !excludedNames.includes(i.itemName)
      && (!filter || i.partCode === filter),
  );

  async function save() {
    if (!selected || saving) return;
    setSaving(true);
    try {
      const { data } = await workoutGenerationApi.addExclusion(selected.itemName, reason.trim() || undefined);
      onAdded(data);
    } catch (e) {
      Alert.alert('エラー', errorMessage(e, '除外できませんでした'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <KeyboardAvoidingView style={styles.flex1} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.dim}>
          <View style={[styles.sheet, !selected && styles.sheetTall]}>
            <Text style={styles.sheetTitle}>除外する種目を追加</Text>
            {selected ? (
              <>
                <View style={styles.sheetItem}>
                  <Text style={styles.sheetItemName}>{selected.itemName}</Text>
                  <Text style={styles.sheetItemSub}>{partName[selected.partCode] ?? selected.partCode}</Text>
                </View>
                <Text style={styles.sheetText}>理由（任意）</Text>
                <TextInput
                  style={styles.reasonInput}
                  value={reason}
                  onChangeText={setReason}
                  maxLength={200}
                  placeholder="例: 右肩に違和感がある"
                  placeholderTextColor="#bbb"
                />
                <View style={styles.row2}>
                  <TouchableOpacity style={[styles.btn, styles.btnG, styles.flex1]} onPress={onClose}>
                    <Text style={styles.btnGText}>キャンセル</Text>
                  </TouchableOpacity>
                  <TouchableOpacity style={[styles.btn, styles.btnP, styles.flex1]} onPress={save} disabled={saving}>
                    <Text style={styles.btnPText}>除外する</Text>
                  </TouchableOpacity>
                </View>
              </>
            ) : (
              <>
                <View style={styles.chips}>
                  {[''].concat(parts).map((code) => (
                    <TouchableOpacity
                      key={code || 'all'}
                      style={[styles.chip, filter === code && styles.chipOn]}
                      onPress={() => setFilter(code)}
                    >
                      <Text style={[styles.chipText, filter === code && styles.chipTextOn]}>
                        {code ? partName[code] ?? code : 'すべて'}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
                <FlatList
                  style={styles.pickList}
                  data={candidates}
                  keyExtractor={(i) => String(i.id)}
                  renderItem={({ item }) => (
                    <TouchableOpacity style={styles.pickRow} onPress={() => setSelected(item)}>
                      <Text style={styles.pickName}>{item.itemName}</Text>
                      <Text style={styles.sheetItemSub}>{partName[item.partCode] ?? item.partCode}</Text>
                    </TouchableOpacity>
                  )}
                />
                <TouchableOpacity style={[styles.btn, styles.btnG]} onPress={onClose}>
                  <Text style={styles.btnGText}>閉じる</Text>
                </TouchableOpacity>
              </>
            )}
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#f5f5f5' },
  muted: { color: '#999' },
  body: { padding: 14, gap: 10, paddingBottom: 32 },
  flex1: { flex: 1 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 12 },
  lbl: { fontSize: 13, color: '#888', fontWeight: '700', marginBottom: 8 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    borderWidth: 1.5, borderColor: '#ddd', borderRadius: 999,
    paddingVertical: 5, paddingHorizontal: 12, backgroundColor: '#fff',
  },
  chipOn: { borderColor: '#4CAF50', backgroundColor: '#e8f5e9' },
  chipText: { fontSize: 14, fontWeight: '700', color: '#666' },
  chipTextOn: { color: '#2e7d32' },
  hint: { fontSize: 12, color: '#999', marginTop: 8 },
  prog: { backgroundColor: '#eef2ff', borderRadius: 10, padding: 10 },
  progText: { fontSize: 13, color: '#3730a3', lineHeight: 19 },
  chk: { flexDirection: 'row', flexWrap: 'wrap', rowGap: 8 },
  chkItem: { width: '50%', flexDirection: 'row', alignItems: 'center', gap: 6 },
  chkText: { fontSize: 14, color: '#333' },
  ex: {
    flexDirection: 'row', alignItems: 'center', paddingVertical: 7,
    borderBottomWidth: 1, borderBottomColor: '#f2f2f2',
  },
  exName: { fontSize: 14, color: '#333' },
  exSub: { fontSize: 12, color: '#999' },
  exX: { fontSize: 18, color: '#bbb', fontWeight: '700', paddingHorizontal: 4 },
  addLink: { color: '#4CAF50', fontWeight: '700', fontSize: 14, marginTop: 8 },
  btn: { borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  btnP: { backgroundColor: '#4CAF50' },
  btnPText: { color: '#fff', fontSize: 16, fontWeight: '800' },
  btnG: { backgroundColor: '#f0f0f0' },
  btnGText: { color: '#666', fontSize: 15, fontWeight: '700' },
  btnDisabled: { opacity: 0.6 },
  row2: { flexDirection: 'row', gap: 10 },
  dim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'center', padding: 18 },
  sheet: { backgroundColor: '#fff', borderRadius: 16, padding: 18, gap: 12 },
  sheetTall: { maxHeight: '80%' },
  sheetTitle: { fontSize: 17, fontWeight: '800', color: '#222' },
  sheetText: { fontSize: 14, color: '#555' },
  sheetItem: { backgroundColor: '#f7f7f7', borderRadius: 10, padding: 10 },
  sheetItemName: { fontSize: 16, fontWeight: '800', color: '#222' },
  sheetItemSub: { fontSize: 12, color: '#888' },
  reasonInput: {
    borderWidth: 1, borderColor: '#e3e3e3', borderRadius: 10,
    paddingHorizontal: 10, paddingVertical: 9, fontSize: 14, color: '#222',
  },
  pickList: { maxHeight: 360 },
  pickRow: { paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#f2f2f2' },
  pickName: { fontSize: 15, color: '#222' },
});
