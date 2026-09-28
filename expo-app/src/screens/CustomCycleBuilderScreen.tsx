import React, { useCallback, useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput, Switch, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { periodizationApi } from '../api/client';
import type { CustomCycleRules, PeriodizationItemInput, PeriodizationProposal } from '../api/types';
import DayItemsSheet from '../components/DayItemsSheet';
import { DAY_CODES, DAY_LABELS, partLabel, errorMessage } from '../components/PeriodizationShared';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'CustomCycleBuilder'>;
};

type DayState = { partCode: string | null; items: PeriodizationItemInput[] };

/**
 * 白紙から自分で組む（機能見直し-1-#3、モックアップ版11の06のモバイル版）。
 * 週数の範囲・補足・注意表示の条件と文言はサーバーの /custom/rules から取得する（Web版と同じ）。
 */
export default function CustomCycleBuilderScreen({ navigation }: Props) {
  const [rules, setRules] = useState<CustomCycleRules | null>(null);
  const [scheduled, setScheduled] = useState<PeriodizationProposal | null>(null);
  const [name, setName] = useState('');
  const [totalWeeks, setTotalWeeks] = useState(4);
  const [pct, setPct] = useState<Record<number, string>>({});
  const [deload, setDeload] = useState<Record<number, boolean>>({});
  const [days, setDays] = useState<Record<number, Record<string, DayState>>>({});
  const [currentWeek, setCurrentWeek] = useState(1);
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useFocusEffect(useCallback(() => {
    (async () => {
      try {
        const [{ data: r }, { data: p }] = await Promise.all([
          periodizationApi.customRules(), periodizationApi.proposals(),
        ]);
        setRules(r);
        setScheduled(p.scheduled[0] ?? null);
      } catch (e) {
        setError(errorMessage(e, '読み込めませんでした'));
      }
    })();
  }, []));

  const day = (w: number, code: string): DayState => days[w]?.[code] ?? { partCode: null, items: [] };

  const warnings = useMemo(() => {
    if (!rules) return [] as { note: boolean; text: string }[];
    const flags = Array.from({ length: totalWeeks }, (_, i) => !!deload[i + 1]);
    const out: { note: boolean; text: string }[] = [];
    if (totalWeeks === rules.minTotalWeeks) out.push({ note: true, text: rules.shortCycleNote });
    let streak = 0; let maxStreak = 0;
    flags.forEach((d) => { if (d) streak = 0; else { streak += 1; maxStreak = Math.max(maxStreak, streak); } });
    if (maxStreak >= rules.longLoadStreakWarnWeeks) out.push({ note: false, text: rules.longLoadStreakWarning });
    if (totalWeeks >= rules.noDeloadWarnMinWeeks && !flags.some(Boolean)) out.push({ note: false, text: rules.noDeloadWarning });
    return out;
  }, [rules, totalWeeks, deload]);

  const copyWeek1 = () => {
    const src = days[1] ?? {};
    const next: Record<number, Record<string, DayState>> = { 1: src };
    for (let w = 2; w <= totalWeeks; w += 1) {
      next[w] = {};
      Object.keys(src).forEach((code) => {
        next[w][code] = { partCode: src[code].partCode, items: src[code].items.map((i) => ({ ...i })) };
      });
    }
    setDays(next);
  };

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      const weeks = Array.from({ length: totalWeeks }, (_, i) => {
        const n = i + 1;
        const v = pct[n];
        return { weekNumber: n, targetIntensityPct: v ? Number(v) : null, deload: !!deload[n] };
      });
      const dayList: { weekNumber: number; dayOfWeek: string; partCode: string; items: PeriodizationItemInput[] }[] = [];
      for (let w = 1; w <= totalWeeks; w += 1) {
        DAY_CODES.forEach((code) => {
          const d = day(w, code);
          if (d.partCode) dayList.push({ weekNumber: w, dayOfWeek: code, partCode: d.partCode, items: d.items });
        });
      }
      await periodizationApi.createCustom({ name: name.trim(), totalWeeks, weeks, days: dayList });
      navigation.replace('ProgramCycle');
    } catch (e) {
      setConfirming(false);
      setError(errorMessage(e, '作成に失敗しました'));
    } finally {
      setSaving(false);
    }
  };

  if (!rules) {
    return (
      <SafeAreaView style={styles.safe}>
        {error ? <Text style={styles.error}>{error}</Text> : <View style={styles.center}><ActivityIndicator size="large" color="#4CAF50" /></View>}
      </SafeAreaView>
    );
  }

  const editingDay = editing ? day(currentWeek, editing) : null;

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< 戻る'}</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>自分で組む</Text>
        <View style={{ width: 50 }} />
      </View>
      {/* 数値キーボードには閉じるボタンが無いため、スクロール操作でキーボードを閉じられるようにする */}
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled" keyboardDismissMode="on-drag">
        {error && <Text style={styles.error}>{error}</Text>}
        <View style={styles.card}>
          <Text style={styles.label}>名前</Text>
          <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="例: 自作 筋肥大4週" placeholderTextColor="#aaa" maxLength={100} />
          <Text style={[styles.label, { marginTop: 12 }]}>週数</Text>
          <View style={styles.stepperRow}>
            <View style={styles.stepper}>
              <TouchableOpacity onPress={() => { const n = Math.max(rules.minTotalWeeks, totalWeeks - 1); setTotalWeeks(n); setCurrentWeek((c) => Math.min(c, n)); }} accessibilityLabel="週数を減らす">
                <Text style={styles.stepperBtn}>−</Text>
              </TouchableOpacity>
              <Text style={styles.stepperText}>{totalWeeks} 週</Text>
              <TouchableOpacity onPress={() => setTotalWeeks(Math.min(rules.maxTotalWeeks, totalWeeks + 1))} accessibilityLabel="週数を増やす">
                <Text style={styles.stepperBtn}>＋</Text>
              </TouchableOpacity>
            </View>
            <Text style={styles.muted}>{rules.minTotalWeeks}〜{rules.maxTotalWeeks}週</Text>
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.sectionTitle}>週ごとの強度（%1RM）</Text>
          {Array.from({ length: totalWeeks }, (_, i) => i + 1).map((n) => (
            <View key={n} style={[styles.weekRow, deload[n] && styles.deloadBg]}>
              <Text style={styles.weekLabel}>週{n}</Text>
              <TextInput
                style={styles.pctInput}
                keyboardType="decimal-pad"
                value={pct[n] ?? ''}
                onChangeText={(v) => setPct((p) => ({ ...p, [n]: v }))}
                placeholder="70.0"
                placeholderTextColor="#bbb"
                accessibilityLabel={`週${n}の目標強度`}
              />
              <Text style={styles.muted}>ディロード</Text>
              <Switch value={!!deload[n]} onValueChange={(v) => setDeload((d) => ({ ...d, [n]: v }))} />
            </View>
          ))}
          {warnings.map((w) => (
            <Text key={w.text} style={w.note ? styles.note : styles.caution}>{w.text}</Text>
          ))}
        </View>

        <View style={styles.card}>
          <View style={styles.rowBetween}>
            <Text style={styles.sectionTitle}>曜日の割り当て</Text>
            <TouchableOpacity onPress={copyWeek1}>
              <Text style={styles.link}>第1週を全週にコピー</Text>
            </TouchableOpacity>
          </View>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginVertical: 10 }}>
            {Array.from({ length: totalWeeks }, (_, i) => i + 1).map((n) => (
              <TouchableOpacity
                key={n}
                style={[styles.tab, deload[n] && styles.tabDeload, n === currentWeek && (deload[n] ? styles.tabDeloadActive : styles.tabActive)]}
                onPress={() => setCurrentWeek(n)}
              >
                <Text style={[styles.tabText, n === currentWeek && !deload[n] && styles.tabTextActive]}>週{n}{deload[n] ? ' D' : ''}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
          {DAY_CODES.map((code) => {
            const d = day(currentWeek, code);
            return (
              <TouchableOpacity key={code} style={styles.dayRow} onPress={() => setEditing(code)} accessibilityLabel={`${DAY_LABELS[code]}曜日を編集`}>
                <Text style={[styles.dayChip, !d.partCode && styles.dayChipRest]}>{DAY_LABELS[code]}</Text>
                <Text style={[styles.dayText, !d.partCode && styles.restText]} numberOfLines={1}>
                  {d.partCode
                    ? `${partLabel(d.partCode)}・${d.items.length ? `${d.items[0].itemName}${d.items.length > 1 ? ` 他${d.items.length - 1}種目` : ''}` : '種目未設定'}`
                    : '休養'}
                </Text>
                <Text style={styles.link}>編集</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {confirming && scheduled && (
          <View style={styles.confirm}>
            <Text style={styles.confirmText}>
              予約中の「{scheduled.name}」（{scheduled.trainerName ?? ''}トレーナーの案）は取り消されます。
              {scheduled.trainerName ?? ''}トレーナーの画面には「本人が別のプログラムに切り替えたため取り消し」と表示されます。
            </Text>
            <View style={styles.actions}>
              <TouchableOpacity style={[styles.btn, styles.btnGhost, { flex: 1 }]} onPress={() => setConfirming(false)}>
                <Text style={styles.btnGhostText}>やめる</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.btn, styles.btnPrimary, { flex: 1 }]} onPress={submit}>
                <Text style={styles.btnPrimaryText}>取り消して切り替える</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </ScrollView>
      <View style={styles.footer}>
        <TouchableOpacity
          style={[styles.btn, styles.btnPrimary]}
          disabled={saving}
          onPress={() => (scheduled ? setConfirming(true) : submit())}
        >
          {saving ? <ActivityIndicator color="#fff" /> : <Text style={styles.btnPrimaryText}>この内容で開始する</Text>}
        </TouchableOpacity>
      </View>

      <DayItemsSheet
        visible={!!editing}
        title={editing ? `${DAY_LABELS[editing]}曜日` : ''}
        subTitle={`第${currentWeek}週・部位と種目`}
        editablePart
        partCode={editingDay?.partCode ?? null}
        items={editingDay?.items ?? []}
        onClose={() => setEditing(null)}
        onSave={async (items, partCode) => {
          if (!editing) return;
          const code = editing;
          setDays((prev) => ({
            ...prev,
            [currentWeek]: { ...(prev[currentWeek] ?? {}), [code]: { partCode, items } },
          }));
          setEditing(null);
        }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backText: { fontSize: 14, color: '#4CAF50', fontWeight: '600', width: 50 },
  headerTitle: { fontSize: 16, fontWeight: '700', color: '#222' },
  content: { padding: 14, paddingBottom: 24 },
  error: { backgroundColor: '#fde8e8', color: '#b91c1c', padding: 10, borderRadius: 8, fontSize: 13, margin: 10 },
  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12 },
  label: { fontSize: 12, color: '#888', marginBottom: 6 },
  input: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 9, fontSize: 14, color: '#222' },
  stepperRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  stepper: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: '#ddd', borderRadius: 8 },
  stepperBtn: { fontSize: 18, color: '#4CAF50', paddingHorizontal: 14, paddingVertical: 4 },
  stepperText: { fontSize: 14, color: '#222', minWidth: 48, textAlign: 'center' },
  muted: { fontSize: 12, color: '#888' },
  sectionTitle: { fontSize: 14, fontWeight: '800', color: '#222' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  weekRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#f3f3f3' },
  deloadBg: { backgroundColor: '#fef3e2' },
  weekLabel: { fontSize: 13, color: '#333', width: 40 },
  pctInput: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6, width: 80, textAlign: 'right', fontSize: 14, color: '#222' },
  note: { fontSize: 12, color: '#777', marginTop: 10 },
  caution: { fontSize: 12, color: '#92400e', backgroundColor: '#fef3e2', borderRadius: 8, padding: 8, marginTop: 10, lineHeight: 18 },
  link: { fontSize: 13, color: '#4CAF50', fontWeight: '600' },
  tab: { borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6, marginRight: 6, backgroundColor: '#fff' },
  tabActive: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  tabDeload: { borderColor: '#f59e0b', backgroundColor: '#fef3e2' },
  tabDeloadActive: { backgroundColor: '#f59e0b', borderColor: '#f59e0b' },
  tabText: { fontSize: 12, color: '#666' },
  tabTextActive: { color: '#fff', fontWeight: '700' },
  dayRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#f0f0f0' },
  dayChip: { width: 30, height: 30, borderRadius: 8, backgroundColor: '#f0f0f0', textAlign: 'center', lineHeight: 30, fontWeight: '800', color: '#333', overflow: 'hidden' },
  dayChipRest: { color: '#bbb' },
  dayText: { flex: 1, fontSize: 13, fontWeight: '600', color: '#222' },
  restText: { color: '#bbb', fontWeight: '400' },
  confirm: { backgroundColor: '#fef3e2', borderWidth: 1.5, borderColor: '#f59e0b', borderRadius: 10, padding: 12 },
  confirmText: { fontSize: 13, color: '#333', lineHeight: 20 },
  actions: { flexDirection: 'row', gap: 8, marginTop: 10 },
  footer: { padding: 14, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#eee' },
  btn: { borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  btnPrimary: { backgroundColor: '#4CAF50' },
  btnPrimaryText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  btnGhost: { borderWidth: 1, borderColor: '#4CAF50', backgroundColor: '#fff' },
  btnGhostText: { color: '#4CAF50', fontWeight: '700', fontSize: 14 },
});
