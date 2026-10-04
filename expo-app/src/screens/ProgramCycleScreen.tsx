import React, { useCallback, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { periodizationApi } from '../api/client';
import type {
  PeriodizationCycle, PeriodizationToday, PeriodizationProposals, PeriodizationContent,
  PeriodizationDay, ProposalResponseChoice,
} from '../api/types';
import DayItemsSheet from '../components/DayItemsSheet';
import {
  DAY_CODES, DAY_LABELS, partLabel, fmtMonthDay, dayBeforeMonthDay, nextStartDate, tierLabel, errorMessage,
} from '../components/PeriodizationShared';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'ProgramCycle'>;
};

/**
 * 期分けプログラム（機能見直し-1-#3、モックアップ版11の02/03/04/08のモバイル版）。
 * - 最上部: トレーナーからの案（予約中の表示・返事待ちの案カード）
 * - サイクル終了時: 3択（同じ内容で継続／別のプリセット／自分で組む）。モバイルは「通常の週間プログラムに戻る」を出さない
 * - 実施中: 週送りタイムライン。曜日カードのタップで種目編集シート（03）
 * - 未採用かつ案が無い: PresetSelectionScreenへ
 */
export default function ProgramCycleScreen({ navigation }: Props) {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [today, setToday] = useState<PeriodizationToday | null>(null);
  const [cycle, setCycle] = useState<PeriodizationCycle | null>(null);
  const [proposals, setProposals] = useState<PeriodizationProposals>({ pending: [], scheduled: [] });
  const [week, setWeek] = useState(1);
  const [editingDay, setEditingDay] = useState<PeriodizationDay | null>(null);
  const [confirm, setConfirm] = useState<null | { kind: 'decline' | 'switch'; onYes: () => void }>(null);
  const [content, setContent] = useState<Record<number, PeriodizationContent | undefined>>({});
  const scrollRef = useRef<ScrollView>(null);
  /** 確認は画面最上部に出すため、表示時に先頭までスクロールして見落としを防ぐ */
  const openConfirm = (c: { kind: 'decline' | 'switch'; onYes: () => void }) => {
    setConfirm(c);
    scrollRef.current?.scrollTo({ y: 0, animated: true });
  };

  const load = useCallback(async () => {
    setError(null);
    try {
      // today を先に呼ぶ（サーバー側でサイクル終了の判定・予約した案の自動開始が行われるため）
      const { data: t } = await periodizationApi.today();
      const [{ data: c }, { data: p }] = await Promise.all([
        periodizationApi.cycle(), periodizationApi.proposals(),
      ]);
      const activeCycle = c ? (c as PeriodizationCycle) : null;
      setToday(t);
      setCycle(activeCycle);
      setProposals(p);
      if (activeCycle) setWeek(activeCycle.currentWeekNumber);
      if (!t.cycleCompleted && !activeCycle && p.pending.length === 0) {
        navigation.replace('PresetSelection', {});
        return;
      }
    } catch (e) {
      setError(errorMessage(e, 'プログラムを読み込めませんでした'));
    } finally {
      setLoading(false);
    }
  }, [navigation]);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  const scheduled = proposals.scheduled[0];
  const pending = proposals.pending[0];
  const hasActive = !!today?.hasActiveCycle;

  /** 予約中の案があるときは、本人による切り替えの前に確認を出す（2026-09-26 USER確定） */
  const withSwitchConfirm = (proceed: () => void) => {
    if (!scheduled) { proceed(); return; }
    openConfirm({ kind: 'switch', onYes: () => { setConfirm(null); proceed(); } });
  };

  const respond = async (choice: ProposalResponseChoice) => {
    if (!pending) return;
    try {
      await periodizationApi.respond(pending.id, choice);
      setConfirm(null);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const toggleContent = async (id: number) => {
    if (content[id]) { setContent((prev) => ({ ...prev, [id]: undefined })); return; }
    try {
      const { data } = await periodizationApi.proposalContent(id);
      setContent((prev) => ({ ...prev, [id]: data }));
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  const renderContent = (id: number) => {
    const c = content[id];
    if (!c) return null;
    return (
      <View style={styles.contentBox}>
        {c.weeks.map((w) => (
          <View key={w.weekNumber} style={[styles.contentRow, w.deload && styles.deloadBg]}>
            <Text style={styles.contentWeek}>
              第{w.weekNumber}週 {w.targetIntensityPct != null ? `${w.targetIntensityPct.toFixed(1)}%` : ''}{w.deload ? '（ディロード）' : ''}
            </Text>
            {c.days.filter((d) => d.weekNumber === w.weekNumber).map((d) => (
              <Text key={d.dayOfWeek} style={styles.contentDay}>
                {DAY_LABELS[d.dayOfWeek]} {partLabel(d.partCode)}: {d.items.map((i) => `${i.itemName}×${i.targetSets}`).join('、')}
              </Text>
            ))}
          </View>
        ))}
      </View>
    );
  };

  const confirmBox = () => {
    if (!confirm) return null;
    const text = confirm.kind === 'decline'
      ? `「${pending?.name ?? ''}」の案を断りますか？`
      : `予約中の「${scheduled?.name ?? ''}」（${scheduled?.trainerName ?? ''}トレーナーの案）は取り消されます。${scheduled?.trainerName ?? ''}トレーナーの画面には「本人が別のプログラムに切り替えたため取り消し」と表示されます。`;
    return (
      <View style={styles.confirm}>
        <Text style={styles.confirmText}>{text}</Text>
        <View style={styles.actions}>
          <TouchableOpacity style={[styles.btn, styles.btnGhost]} onPress={() => setConfirm(null)}>
            <Text style={styles.btnGhostText}>やめる</Text>
          </TouchableOpacity>
          <TouchableOpacity style={[styles.btn, styles.btnPrimary]} onPress={confirm.onYes}>
            <Text style={styles.btnPrimaryText}>{confirm.kind === 'decline' ? '断る' : '取り消して切り替える'}</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  const renderProposals = () => (
    <>
      {scheduled && (
        <View style={styles.proposalCard} testID="scheduled-card">
          <View style={styles.rowBetween}>
            <Text style={[styles.badge, styles.badgeScheduled]}>予約中</Text>
            {scheduled.contentUpdatedAfterResponse && (
              <Text style={styles.muted}>トレーナーが内容を更新しました（{fmtMonthDay(scheduled.contentUpdatedDate)}）</Text>
            )}
          </View>
          <Text style={styles.body}>
            「{scheduled.name}」は、今のプログラム（{dayBeforeMonthDay(scheduled.scheduledStartDate)}まで）が終わった翌日の
            <Text style={styles.bold}>{fmtMonthDay(scheduled.scheduledStartDate)}から</Text>始まります。
          </Text>
          <Text style={styles.muted}>
            しばらくアプリを開かなかった場合も{fmtMonthDay(scheduled.scheduledStartDate)}開始として扱うため、開いた時点で途中の週から始まります。
          </Text>
          <TouchableOpacity onPress={() => toggleContent(scheduled.id)}>
            <Text style={styles.link}>案の中身を見る ›</Text>
          </TouchableOpacity>
          {renderContent(scheduled.id)}
        </View>
      )}
      {pending && (
        <View style={styles.proposalCard} testID="proposal-card">
          <Text style={[styles.badge, styles.badgePending]}>トレーナーからの案</Text>
          <Text style={styles.cardTitle}>{pending.name}</Text>
          <Text style={styles.muted}>
            {pending.trainerName ?? ''}トレーナーより・{pending.totalWeeks}週構成
            {pending.contentUpdatedDate ? `・${fmtMonthDay(pending.contentUpdatedDate)}に内容を更新` : ''}
          </Text>
          {hasActive ? (
            <>
              <TouchableOpacity style={[styles.choice, styles.choicePrimary]} onPress={() => withSwitchConfirm(() => respond('START_NOW'))}>
                <Text style={styles.choiceTitle}>今すぐ切り替える</Text>
              </TouchableOpacity>
              {scheduled ? (
                <View style={[styles.choice, styles.choiceDisabled]}>
                  <Text style={styles.choiceTitle}>今のプログラムが終わったら開始</Text>
                  <Text style={styles.choiceDesc}>予約中の「{scheduled.name}」があるため、この案は予約できません</Text>
                </View>
              ) : (
                <TouchableOpacity style={styles.choice} onPress={() => respond('SCHEDULE')}>
                  <Text style={styles.choiceTitle}>今のプログラムが終わったら開始</Text>
                  {cycle && (
                    <Text style={styles.choiceDesc}>
                      {dayBeforeMonthDay(nextStartDate(cycle.startDate, cycle.totalWeeks))}の翌日、{fmtMonthDay(nextStartDate(cycle.startDate, cycle.totalWeeks))}から自動で始まります
                    </Text>
                  )}
                </TouchableOpacity>
              )}
            </>
          ) : (
            <TouchableOpacity style={[styles.choice, styles.choicePrimary]} onPress={() => respond('START_NOW')}>
              <Text style={styles.choiceTitle}>このプログラムで始める</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            style={styles.choice}
            onPress={() => openConfirm({ kind: 'decline', onYes: () => respond('DECLINE') })}
          >
            <Text style={styles.choiceTitle}>断る</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => toggleContent(pending.id)}>
            <Text style={styles.link}>案の中身を見る ›</Text>
          </TouchableOpacity>
          {renderContent(pending.id)}
        </View>
      )}
    </>
  );

  const renderCompleted = () => (
    <View style={styles.card} testID="completed">
      <Text style={styles.cardTitle}>サイクル終了</Text>
      <Text style={styles.muted}>次はどうしますか？</Text>
      <TouchableOpacity
        style={[styles.choice, styles.choicePrimary]}
        onPress={() => withSwitchConfirm(async () => {
          try {
            await periodizationApi.renew('REPEAT_SAME');
            await load();
          } catch (e) { setError(errorMessage(e)); }
        })}
      >
        <Text style={styles.choiceTitle}>同じ内容で継続する</Text>
      </TouchableOpacity>
      <TouchableOpacity style={styles.choice} onPress={() => navigation.navigate('PresetSelection', { renew: true })}>
        <Text style={styles.choiceTitle}>別のプリセットを選ぶ</Text>
      </TouchableOpacity>
      <TouchableOpacity style={styles.choice} onPress={() => navigation.navigate('CustomCycleBuilder', {})}>
        <Text style={styles.choiceTitle}>自分で組む</Text>
      </TouchableOpacity>
    </View>
  );

  const renderTimeline = (c: PeriodizationCycle) => {
    const w = c.weeks.find((x) => x.weekNumber === week) ?? c.weeks[0];
    const warning = today?.stagnationWarning;
    return (
      <View style={styles.card} testID="timeline">
        <Text style={styles.cardTitle}>{c.name}</Text>
        <Text style={styles.muted}>{c.startDate}開始・{tierLabel(c.tier)}・第{c.currentWeekNumber}週 / 全{c.totalWeeks}週</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabs}>
          {c.weeks.map((x) => (
            <TouchableOpacity
              key={x.weekNumber}
              style={[styles.tab, x.deload && styles.tabDeload, x.weekNumber === week && (x.deload ? styles.tabDeloadActive : styles.tabActive)]}
              onPress={() => setWeek(x.weekNumber)}
            >
              <Text style={[styles.tabText, x.deload && styles.tabDeloadText, x.weekNumber === week && !x.deload && styles.tabTextActive]}>
                週{x.weekNumber}{x.deload ? ' D' : ''}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
        {w && (
          <View style={styles.strip}>
            <View>
              <Text style={styles.stripVal}>{w.targetIntensityPct != null ? `${w.targetIntensityPct.toFixed(1)}%` : '-'}</Text>
              <Text style={styles.muted}>目標強度</Text>
            </View>
            {w.deload && <Text style={styles.deloadBadge}>ディロード週</Text>}
          </View>
        )}
        {week === c.currentWeekNumber && (warning === 'MILD' || warning === 'STRONG') && (
          <View style={[styles.banner, warning === 'STRONG' ? styles.bannerStrong : styles.bannerMild]}>
            <Text style={[styles.bannerText, warning === 'STRONG' && styles.bannerStrongText]}>
              {warning === 'STRONG'
                ? '明確に停滞しています。ディロード週を挟むことを推奨します。'
                : 'そろそろディロードを検討してはどうでしょう。'}
            </Text>
            <Text style={[styles.bannerSub, warning === 'STRONG' && styles.bannerStrongText]}>
              {(today?.stagnantItems ?? []).map((i) => `${i.itemName}（${i.level === 'STRONG' ? '明確に停滞' : '伸び悩み'}）`).join('、')}
            </Text>
          </View>
        )}
        {DAY_CODES.map((code) => {
          const d = w?.days.find((x) => x.dayOfWeek === code);
          if (!d || !d.partCode) {
            return (
              <View key={code} style={styles.dayRow}>
                <Text style={[styles.dayChip, styles.dayChipRest]}>{DAY_LABELS[code]}</Text>
                <Text style={styles.restText}>休養日</Text>
              </View>
            );
          }
          const first = d.items[0];
          return (
            <TouchableOpacity key={code} style={styles.dayRow} onPress={() => setEditingDay(d)} accessibilityLabel={`${DAY_LABELS[code]}曜日の種目を編集`}>
              <Text style={styles.dayChip}>{DAY_LABELS[code]}</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.dayItems} numberOfLines={1}>
                  {first ? `${first.itemName}${d.items.length > 1 ? ` 他${d.items.length - 1}種目` : ''}` : '種目が未設定です'}
                </Text>
                <Text style={styles.muted}>
                  {partLabel(d.partCode)}{first?.targetWeightKg != null ? `・${first.itemName} 約${first.targetWeightKg}kg` : ''}
                </Text>
              </View>
              <Text style={styles.link}>編集</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< 戻る'}</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>プログラム</Text>
        <View style={{ width: 50 }} />
      </View>
      {loading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#4CAF50" /></View>
      ) : (
        <ScrollView ref={scrollRef} contentContainerStyle={styles.content}>
          {error && <Text style={styles.error}>{error}</Text>}
          {confirm && confirmBox()}
          {renderProposals()}
          {today?.cycleCompleted
            ? renderCompleted()
            : cycle
              ? renderTimeline(cycle)
              : (
                <View style={styles.card}>
                  <Text style={styles.muted}>まだプログラムを開始していません</Text>
                  <TouchableOpacity style={[styles.choice, styles.choicePrimary]} onPress={() => navigation.navigate('PresetSelection', {})}>
                    <Text style={styles.choiceTitle}>プログラムを選ぶ</Text>
                  </TouchableOpacity>
                </View>
              )}
        </ScrollView>
      )}
      <DayItemsSheet
        visible={!!editingDay}
        title={editingDay ? `${DAY_LABELS[editingDay.dayOfWeek]}曜日の種目編集` : ''}
        subTitle={editingDay ? `第${week}週・${partLabel(editingDay.partCode)}` : ''}
        editablePart={false}
        partCode={editingDay?.partCode ?? null}
        items={editingDay?.items ?? []}
        onClose={() => setEditingDay(null)}
        onSave={async (items) => {
          if (!editingDay) return;
          await periodizationApi.saveDayItems(editingDay.dayTemplateId, items);
          setEditingDay(null);
          const { data } = await periodizationApi.cycle();
          setCycle(data ? (data as PeriodizationCycle) : null);
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
  content: { padding: 14, paddingBottom: 40 },
  error: { backgroundColor: '#fde8e8', color: '#b91c1c', padding: 10, borderRadius: 8, fontSize: 13, marginBottom: 10 },
  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12 },
  proposalCard: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, borderWidth: 1.5, borderColor: '#4CAF50' },
  rowBetween: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
  badge: { alignSelf: 'flex-start', fontSize: 11, paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, overflow: 'hidden' },
  badgePending: { backgroundColor: '#e8f5e9', color: '#2e7d32' },
  badgeScheduled: { backgroundColor: '#fef3e2', color: '#92400e' },
  cardTitle: { fontSize: 16, fontWeight: '800', color: '#222', marginTop: 6 },
  body: { fontSize: 13, color: '#333', marginTop: 8, lineHeight: 20 },
  bold: { fontWeight: '800' },
  muted: { fontSize: 12, color: '#888', marginTop: 2 },
  link: { fontSize: 13, color: '#4CAF50', fontWeight: '600', marginTop: 8 },
  choice: { borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 10, padding: 12, marginTop: 8 },
  choicePrimary: { borderColor: '#4CAF50', backgroundColor: '#e8f5e9' },
  choiceDisabled: { opacity: 0.55 },
  choiceTitle: { fontSize: 14, fontWeight: '700', color: '#222' },
  choiceDesc: { fontSize: 12, color: '#777', marginTop: 2 },
  confirm: { backgroundColor: '#fef3e2', borderWidth: 1.5, borderColor: '#f59e0b', borderRadius: 10, padding: 12, marginBottom: 12 },
  confirmText: { fontSize: 13, color: '#333', lineHeight: 20 },
  actions: { flexDirection: 'row', gap: 8, marginTop: 10 },
  btn: { flex: 1, borderRadius: 10, paddingVertical: 11, alignItems: 'center' },
  btnGhost: { borderWidth: 1, borderColor: '#4CAF50', backgroundColor: '#fff' },
  btnGhostText: { color: '#4CAF50', fontWeight: '700' },
  btnPrimary: { backgroundColor: '#4CAF50' },
  btnPrimaryText: { color: '#fff', fontWeight: '700' },
  contentBox: { marginTop: 8, borderTopWidth: 1, borderTopColor: '#eee' },
  contentRow: { paddingVertical: 6, borderBottomWidth: 1, borderBottomColor: '#f3f3f3' },
  contentWeek: { fontSize: 12, fontWeight: '700', color: '#333' },
  contentDay: { fontSize: 12, color: '#555', marginTop: 2 },
  deloadBg: { backgroundColor: '#fef3e2' },
  tabs: { marginVertical: 12 },
  tab: { borderWidth: 1, borderColor: '#e0e0e0', borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6, marginRight: 6, backgroundColor: '#fff' },
  tabActive: { backgroundColor: '#4CAF50', borderColor: '#4CAF50' },
  tabDeload: { borderColor: '#f59e0b', backgroundColor: '#fef3e2' },
  tabDeloadActive: { backgroundColor: '#f59e0b', borderColor: '#f59e0b' },
  tabText: { fontSize: 12, color: '#666' },
  tabTextActive: { color: '#fff', fontWeight: '700' },
  tabDeloadText: { color: '#92400e' },
  strip: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: '#f5f5f5', borderRadius: 8, padding: 12, marginBottom: 10 },
  stripVal: { fontSize: 20, fontWeight: '800', color: '#222' },
  deloadBadge: { backgroundColor: '#f59e0b', color: '#3a2a08', fontSize: 11, fontWeight: '700', paddingHorizontal: 8, paddingVertical: 2, borderRadius: 999, overflow: 'hidden' },
  banner: { borderRadius: 8, padding: 10, marginBottom: 10 },
  bannerMild: { backgroundColor: '#fef3e2' },
  bannerStrong: { backgroundColor: '#fde8e8' },
  bannerText: { fontSize: 13, fontWeight: '700', color: '#92400e' },
  bannerSub: { fontSize: 12, color: '#92400e', marginTop: 2 },
  bannerStrongText: { color: '#b91c1c' },
  dayRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#f0f0f0' },
  dayChip: { width: 30, height: 30, borderRadius: 8, backgroundColor: '#f0f0f0', textAlign: 'center', lineHeight: 30, fontWeight: '800', color: '#333', overflow: 'hidden' },
  dayChipRest: { color: '#bbb' },
  restText: { fontSize: 13, color: '#bbb' },
  dayItems: { fontSize: 13, fontWeight: '700', color: '#222' },
});
