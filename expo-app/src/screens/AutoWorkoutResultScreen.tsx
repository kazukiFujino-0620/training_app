import React, { useState } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet, Alert, Modal, TextInput,
  KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { CommonActions } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AppStackParamList } from '../navigation/AppNavigator';
import type { AutogenAlternative, AutogenItem } from '../api/types';
import { EditableRow, modeLabel, toSuggestion, totalSets } from '../components/autoWorkoutShared';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'AutoWorkoutResult'>;
  route: RouteProp<AppStackParamList, 'AutoWorkoutResult'>;
};

/** 結果画面で持つ1種目（編集中の値＋理由・注意文・入れ替え候補）。 */
interface Row extends EditableRow {
  key: number;
  reason: string;
  warning: string | null;
  alternatives: AutogenAlternative[];
}

const WARMUP_NOTE = '最初の種目は軽い重量で2セットほど体を慣らしてください。';

function weightText(weightKg: number | null): string {
  return weightKg == null ? '' : String(weightKg);
}

function toRow(item: AutogenItem, key: number): Row {
  return {
    key,
    itemName: item.itemName,
    partCode: item.partCode,
    partName: item.partName,
    compound: item.compound,
    bodyweight: item.bodyweight,
    weight: weightText(item.weightKg),
    reps: String(item.reps),
    sets: String(item.sets),
    reason: item.reason,
    warning: item.warning,
    alternatives: item.alternatives,
  };
}

function altKind(a: Pick<AutogenAlternative, 'bodyweight' | 'compound'>): string {
  if (a.bodyweight) return '自重';
  return a.compound ? '複合' : '単関節';
}

/**
 * 機能見直し-1-#4: 組んだメニューの確認・編集（仮画面D・E・F・G）。
 * 「この内容で反映する」で、今日のおすすめメニューと同じく種目追加画面に入った状態で開く（保存は種目追加画面で行う）。
 */
export default function AutoWorkoutResultScreen({ navigation, route }: Props) {
  const { result, date } = route.params;
  const [rows, setRows] = useState<Row[]>(() => result.items.map(toRow));
  const [swapKey, setSwapKey] = useState<number | null>(null);

  if (result.status !== 'OK') {
    const tooShort = result.status === 'TOO_SHORT';
    return (
      <SafeAreaView style={styles.container} edges={['bottom']}>
        <View style={styles.body}>
          <View style={[styles.card, styles.emptyCard]}>
            <Text style={styles.emptyTitle}>
              {tooShort ? '時間が短いため生成できません' : '条件に合う種目が見つかりません'}
            </Text>
            {!tooShort && (
              <Text style={styles.emptyText}>器具の選択を増やすか、除外している種目を見直してください。</Text>
            )}
          </View>
          <TouchableOpacity style={[styles.btn, styles.btnG]} onPress={() => navigation.goBack()}>
            <Text style={styles.btnGText}>条件を変えて組み直す</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  function update(key: number, field: 'weight' | 'reps' | 'sets', value: string) {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, [field]: value } : r)));
  }

  function remove(key: number) {
    setRows((prev) => prev.filter((r) => r.key !== key));
  }

  function swap(key: number, alt: AutogenAlternative) {
    setRows((prev) => prev.map((r) => {
      if (r.key !== key) return r;
      const previousAsAlt: AutogenAlternative = {
        itemName: r.itemName,
        partCode: r.partCode,
        compound: r.compound,
        bodyweight: r.bodyweight,
        reps: Number(r.reps) || alt.reps,
        weightKg: r.weight.trim() === '' ? null : Number(r.weight),
        restSeconds: alt.restSeconds,
        reason: r.reason,
      };
      return {
        ...r,
        itemName: alt.itemName,
        compound: alt.compound,
        bodyweight: alt.bodyweight,
        weight: weightText(alt.weightKg),
        reps: String(alt.reps),
        reason: alt.reason,
        alternatives: [previousAsAlt, ...r.alternatives.filter((a) => a.itemName !== alt.itemName)],
      };
    }));
    setSwapKey(null);
  }

  function reflect() {
    const { items, errors } = toSuggestion(rows);
    if (errors.length > 0) {
      Alert.alert('入力を確認してください', `${errors.join('、')} の重量・回数・セットを確認してください`);
      return;
    }
    // 種目追加画面で保存した後はトレーニング一覧へ戻るよう、条件・結果の画面は残さない
    const top = navigation.getState().routes[0];
    navigation.dispatch(
      CommonActions.reset({
        index: 1,
        routes: [
          { key: top.key, name: top.name, params: top.params },
          { name: 'AddExercise', params: { aiSuggestion: { items }, date } },
        ],
      }),
    );
  }

  const swapRow = rows.find((r) => r.key === swapKey) ?? null;
  const inMenu = new Set(rows.map((r) => r.itemName));
  const swapOptions = swapRow ? swapRow.alternatives.filter((a) => !inMenu.has(a.itemName)) : [];

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      <KeyboardAvoidingView style={styles.flex1} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.body} keyboardShouldPersistTaps="handled">
          <View style={styles.sum}>
            <Text style={styles.sumText}>{rows.length}種目・{totalSets(rows)}セット</Text>
            <Text style={[styles.mode, result.mode !== 'ADHOC' && styles.modePg]}>{modeLabel(result)}</Text>
          </View>
          <Text style={styles.tot}>
            目安 約{result.estimatedMinutes}分（使える時間 {result.availableMinutes}分）
          </Text>
          {result.notice ? (
            <View style={styles.notice} testID="autogen-notice">
              <Text style={styles.noticeText}>{result.notice}</Text>
            </View>
          ) : null}
          <View style={styles.warm}>
            <Text style={styles.warmText}>{WARMUP_NOTE}</Text>
          </View>

          {rows.map((r) => (
            <View key={r.key} style={styles.it} testID={`autogen-row-${r.itemName}`}>
              <View style={styles.itH}>
                <Text style={styles.itN}>
                  {r.itemName}
                  <Text style={styles.itPart}>  {r.partName}</Text>
                </Text>
                <View style={styles.itA}>
                  {r.alternatives.some((a) => !inMenu.has(a.itemName)) && (
                    <TouchableOpacity onPress={() => setSwapKey(r.key)} hitSlop={{ top: 8, bottom: 8, left: 6, right: 6 }}>
                      <Text style={styles.sw}>入れ替え</Text>
                    </TouchableOpacity>
                  )}
                  <TouchableOpacity onPress={() => remove(r.key)} hitSlop={{ top: 8, bottom: 8, left: 6, right: 6 }}>
                    <Text style={styles.del}>削除</Text>
                  </TouchableOpacity>
                </View>
              </View>
              <View style={styles.fields}>
                <View style={styles.f}>
                  <Text style={styles.fLabel}>重量</Text>
                  {r.bodyweight ? (
                    <Text style={styles.fBlank}>自重</Text>
                  ) : (
                    <View style={styles.fRow}>
                      <TextInput
                        style={styles.fInput}
                        value={r.weight}
                        onChangeText={(v) => update(r.key, 'weight', v)}
                        keyboardType="decimal-pad"
                        placeholder="未入力"
                        placeholderTextColor="#bbb"
                        accessibilityLabel={`${r.itemName}の重量`}
                      />
                      {r.weight !== '' && <Text style={styles.fUnit}>kg</Text>}
                    </View>
                  )}
                </View>
                <View style={styles.f}>
                  <Text style={styles.fLabel}>回数</Text>
                  <TextInput
                    style={styles.fInput}
                    value={r.reps}
                    onChangeText={(v) => update(r.key, 'reps', v)}
                    keyboardType="number-pad"
                    accessibilityLabel={`${r.itemName}の回数`}
                  />
                </View>
                <View style={styles.f}>
                  <Text style={styles.fLabel}>セット</Text>
                  <TextInput
                    style={styles.fInput}
                    value={r.sets}
                    onChangeText={(v) => update(r.key, 'sets', v)}
                    keyboardType="number-pad"
                    accessibilityLabel={`${r.itemName}のセット`}
                  />
                </View>
              </View>
              <Text style={styles.rsn}>{r.reason}</Text>
              {r.warning ? <Text style={[styles.rsn, styles.rsnWarn]}>{r.warning}</Text> : null}
            </View>
          ))}

          <TouchableOpacity
            style={[styles.btn, styles.btnP, rows.length === 0 && styles.btnDisabled]}
            onPress={reflect}
            disabled={rows.length === 0}
          >
            <Text style={styles.btnPText}>この内容で反映する</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.btn, styles.btnG]} onPress={() => navigation.goBack()}>
            <Text style={styles.btnGText}>条件を変えて組み直す</Text>
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>

      {/* F. 入れ替え */}
      <Modal visible={!!swapRow} transparent animationType="fade" onRequestClose={() => setSwapKey(null)}>
        <View style={styles.dim}>
          {swapRow && (
            <View style={styles.sheet}>
              <Text style={styles.sheetTitle}>入れ替える種目</Text>
              <Text style={styles.sheetText}>{swapRow.itemName}（{swapRow.partName}）の代わり</Text>
              {swapOptions.map((a) => (
                <View key={a.itemName} style={styles.alt}>
                  <View style={styles.flex1}>
                    <Text style={styles.altName}>{a.itemName}</Text>
                    <Text style={styles.altSub}>{swapRow.partName}・{altKind(a)}</Text>
                  </View>
                  <TouchableOpacity onPress={() => swap(swapRow.key, a)}>
                    <Text style={styles.pick}>これにする</Text>
                  </TouchableOpacity>
                </View>
              ))}
              <TouchableOpacity style={[styles.btn, styles.btnG]} onPress={() => setSwapKey(null)}>
                <Text style={styles.btnGText}>閉じる</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f5f5f5' },
  flex1: { flex: 1 },
  body: { padding: 14, gap: 10, paddingBottom: 32 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 12 },
  emptyCard: { alignItems: 'center', paddingVertical: 28 },
  emptyTitle: { fontWeight: '800', fontSize: 15, color: '#222', marginBottom: 6 },
  emptyText: { fontSize: 13, color: '#666', textAlign: 'center' },
  sum: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sumText: { fontSize: 18, fontWeight: '800', color: '#222' },
  mode: {
    fontSize: 12, fontWeight: '700', borderRadius: 4, paddingHorizontal: 8, paddingVertical: 2,
    backgroundColor: '#f0f0f0', color: '#555', overflow: 'hidden',
  },
  modePg: { backgroundColor: '#eef2ff', color: '#4f46e5' },
  tot: { fontSize: 13, color: '#666', textAlign: 'right' },
  warm: { backgroundColor: '#fff8e1', borderRadius: 10, padding: 10 },
  notice: { backgroundColor: '#eef2ff', borderRadius: 10, padding: 10 },
  noticeText: { fontSize: 13, color: '#3730a3' },
  warmText: { fontSize: 13, color: '#6b5200' },
  it: { backgroundColor: '#fff', borderRadius: 12, padding: 12, gap: 7 },
  itH: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8 },
  itN: { flex: 1, fontWeight: '800', fontSize: 15, color: '#222' },
  itPart: { fontWeight: '700', color: '#999', fontSize: 12 },
  itA: { flexDirection: 'row', gap: 14 },
  sw: { color: '#4f46e5', fontWeight: '700', fontSize: 13 },
  del: { color: '#c62828', fontWeight: '700', fontSize: 13 },
  fields: { flexDirection: 'row', gap: 8 },
  f: {
    flex: 1, borderWidth: 1, borderColor: '#e3e3e3', borderRadius: 8,
    paddingVertical: 4, paddingHorizontal: 6, alignItems: 'center',
  },
  fLabel: { fontSize: 11, color: '#999' },
  fRow: { flexDirection: 'row', alignItems: 'baseline' },
  fInput: { fontSize: 16, fontWeight: '700', color: '#222', textAlign: 'center', minWidth: 40, paddingVertical: 2 },
  fUnit: { fontSize: 12, color: '#666' },
  fBlank: { fontSize: 15, color: '#bbb', paddingVertical: 2 },
  rsn: { fontSize: 12, color: '#666' },
  rsnWarn: { color: '#b45309' },
  btn: { borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  btnP: { backgroundColor: '#4CAF50' },
  btnPText: { color: '#fff', fontSize: 16, fontWeight: '800' },
  btnG: { backgroundColor: '#f0f0f0' },
  btnGText: { color: '#666', fontSize: 15, fontWeight: '700' },
  btnDisabled: { opacity: 0.5 },
  dim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'center', padding: 18 },
  sheet: { backgroundColor: '#fff', borderRadius: 16, padding: 18, gap: 10 },
  sheetTitle: { fontSize: 17, fontWeight: '800', color: '#222' },
  sheetText: { fontSize: 14, color: '#555' },
  alt: {
    flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: '#eee',
    borderRadius: 10, padding: 10,
  },
  altName: { fontSize: 14, color: '#222' },
  altSub: { fontSize: 12, color: '#999' },
  pick: { color: '#4CAF50', fontWeight: '700', fontSize: 14 },
});
