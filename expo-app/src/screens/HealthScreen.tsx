import React, { useCallback, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, ActivityIndicator, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { healthApi } from '../api/client';
import type { HealthSummaryResponse } from '../api/types';
import { syncHealthData } from '../health';
import {
  COMPARISON_NOTE,
  CONFIRM_MESSAGE,
  NO_AVERAGE_TEXT,
  SLEEP_MINUTE_OPTIONS,
  formatSleepMinutes,
  restingBpmDiffText,
  sleepDiffText,
  todayDateString,
} from '../health/referenceFormat';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Health'>;
};

function fmtDate(iso: string): string {
  const d = new Date(`${iso.slice(0, 10)}T00:00:00`);
  return d.toLocaleDateString('ja-JP', { month: 'long', day: 'numeric' });
}

function sourceLabel(source: string): string {
  return source === 'MANUAL' ? '手動入力' : '自動同期';
}

/** 手動入力1件分の送信結果（機能見直し-1-#5） */
type SaveOutcome =
  | { kind: 'ok' }
  | { kind: 'confirm'; label: string; retry: () => Promise<unknown> }
  | { kind: 'error'; message: string };

async function trySave(
  label: string,
  send: (confirmed: boolean) => Promise<unknown>,
): Promise<SaveOutcome> {
  try {
    await send(false);
    return { kind: 'ok' };
  } catch (e: any) {
    // サーバーは確認範囲の値を未確認で受け取ると 409 + code=CONFIRMATION_REQUIRED を返す
    if (e.response?.status === 409 && e.response?.data?.code === 'CONFIRMATION_REQUIRED') {
      return { kind: 'confirm', label, retry: () => send(true) };
    }
    return { kind: 'error', message: e.response?.data?.error ?? `${label}の保存に失敗しました` };
  }
}

export default function HealthScreen({ navigation }: Props) {
  const [summary, setSummary] = useState<HealthSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  // 機能見直し-1-#5: 睡眠時間・安静時心拍の手動入力
  const [inputDate, setInputDate] = useState(todayDateString());
  const [sleepHours, setSleepHours] = useState('');
  const [sleepMinutes, setSleepMinutes] = useState<number>(0);
  const [restingBpm, setRestingBpm] = useState('');
  const [saving, setSaving] = useState(false);

  const loadSummary = useCallback(async () => {
    try {
      const { data } = await healthApi.getSummary();
      setSummary(data);
    } catch {
      // 初回同期前は空でも正常のため、エラー表示はしない
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { loadSummary(); }, [loadSummary]));

  async function handleSync() {
    setSyncing(true);
    try {
      const result = await syncHealthData();
      if (!result.ok) {
        Alert.alert(
          'ヘルスケア連携',
          result.reason === 'permission denied'
            ? 'ヘルスケアデータへのアクセスが許可されていません。設定アプリから許可してください。'
            : 'この端末ではヘルスケア連携がご利用いただけません。',
        );
        return;
      }
      await loadSummary();
      Alert.alert('同期完了', `${result.syncedCount}件のデータを同期しました`);
    } catch {
      Alert.alert('エラー', '同期に失敗しました。しばらくしてから再度お試しください');
    } finally {
      setSyncing(false);
    }
  }

  function resetInputs() {
    setSleepHours('');
    setSleepMinutes(0);
    setRestingBpm('');
  }

  async function handleManualSave() {
    const hoursText = sleepHours.trim();
    const bpmText = restingBpm.trim();
    if (hoursText === '' && bpmText === '') {
      Alert.alert('入力エラー', '睡眠時間か安静時心拍のどちらかを入力してください');
      return;
    }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(inputDate)) {
      Alert.alert('入力エラー', '日付はYYYY-MM-DDの形式で入力してください');
      return;
    }
    if (inputDate > todayDateString()) {
      Alert.alert('入力エラー', '日付は今日以前を指定してください');
      return;
    }
    let durationMinutes: number | null = null;
    if (hoursText !== '') {
      if (!/^\d{1,2}$/.test(hoursText)) {
        Alert.alert('入力エラー', '睡眠時間の「時間」は整数で入力してください');
        return;
      }
      durationMinutes = Number(hoursText) * 60 + sleepMinutes;
    }
    let bpm: number | null = null;
    if (bpmText !== '') {
      if (!/^\d{1,3}$/.test(bpmText)) {
        Alert.alert('入力エラー', '安静時心拍は整数で入力してください');
        return;
      }
      bpm = Number(bpmText);
    }

    setSaving(true);
    try {
      const outcomes: SaveOutcome[] = [];
      if (durationMinutes !== null) {
        const minutes = durationMinutes;
        outcomes.push(
          await trySave(`睡眠時間 ${formatSleepMinutes(minutes)}`, (confirmed) =>
            healthApi.saveSleep({ date: inputDate, durationMinutes: minutes, confirmed })),
        );
      }
      if (bpm !== null) {
        const value = bpm;
        outcomes.push(
          await trySave(`安静時心拍 ${value} bpm`, (confirmed) =>
            healthApi.saveRestingHeartRate({ date: inputDate, restingBpm: value, confirmed })),
        );
      }

      const errors = outcomes.filter(
        (o): o is Extract<SaveOutcome, { kind: 'error' }> => o.kind === 'error',
      );
      const confirms = outcomes.filter(
        (o): o is Extract<SaveOutcome, { kind: 'confirm' }> => o.kind === 'confirm',
      );
      const okCount = outcomes.filter((o) => o.kind === 'ok').length;

      if (errors.length > 0) {
        Alert.alert('入力エラー', errors.map((e) => e.message).join('\n'));
      }
      if (okCount > 0) {
        await loadSummary();
      }
      if (confirms.length > 0) {
        // 確認はネイティブのAlertで出す（独自Modalを重ねるとiOSで表示されない問題を避ける。PR#265）
        Alert.alert(
          '入力内容の確認',
          `${confirms.map((c) => c.label).join('\n')}\n${CONFIRM_MESSAGE}`,
          [
            { text: '修正する', style: 'cancel' },
            {
              text: 'このまま保存',
              onPress: async () => {
                try {
                  await Promise.all(confirms.map((c) => c.retry()));
                  if (errors.length === 0) resetInputs();
                } catch (e: any) {
                  Alert.alert('エラー', e.response?.data?.error ?? '保存に失敗しました');
                } finally {
                  await loadSummary();
                }
              },
            },
          ],
        );
        return;
      }
      if (errors.length === 0) {
        resetInputs();
      }
    } finally {
      setSaving(false);
    }
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

  const hasAnyData = summary && (
    summary.weight || summary.steps || summary.heartRate || summary.calories
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< 戻る'}</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>ヘルスケア連携</Text>
        <View style={{ width: 50 }} />
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <TouchableOpacity
          style={[styles.syncButton, syncing && styles.syncButtonDisabled]}
          onPress={handleSync}
          disabled={syncing}
        >
          {syncing ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.syncButtonText}>ヘルスケアデータを同期</Text>
          )}
        </TouchableOpacity>

        {/* 機能見直し-1-#5: 睡眠時間・安静時心拍の手動入力 */}
        <Text style={styles.sectionTitle}>手動で記録する</Text>
        <View style={styles.card}>
          <Text style={styles.inputLabel}>日付（起きた日）</Text>
          <TextInput
            style={styles.input}
            value={inputDate}
            onChangeText={setInputDate}
            placeholder="YYYY-MM-DD"
            autoCapitalize="none"
            accessibilityLabel="日付（起きた日）"
            testID="manual-date-input"
          />

          <Text style={[styles.inputLabel, styles.inputLabelSpaced]}>睡眠時間</Text>
          <Text style={styles.inputHint}>前夜の主な睡眠の時間を入力してください（昼寝は含めません）</Text>
          <View style={styles.inlineRow}>
            <TextInput
              style={[styles.input, styles.inputShort]}
              value={sleepHours}
              onChangeText={setSleepHours}
              keyboardType="number-pad"
              maxLength={2}
              accessibilityLabel="睡眠時間（時間）"
              testID="sleep-hours-input"
            />
            <Text style={styles.unitText}>時間</Text>
          </View>
          <View style={styles.minuteRow} accessibilityLabel="睡眠時間（分）">
            {SLEEP_MINUTE_OPTIONS.map((m) => (
              <TouchableOpacity
                key={m}
                style={[styles.minuteButton, sleepMinutes === m && styles.minuteButtonActive]}
                onPress={() => setSleepMinutes(m)}
                accessibilityRole="button"
                accessibilityState={{ selected: sleepMinutes === m }}
                testID={`sleep-minute-${m}`}
              >
                <Text style={[styles.minuteText, sleepMinutes === m && styles.minuteTextActive]}>
                  {m}分
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <Text style={styles.inputHint}>眠れなかった日は「0時間0分」で記録できます</Text>

          <Text style={[styles.inputLabel, styles.inputLabelSpaced]}>
            安静時心拍（起床時など、落ち着いた状態で）
          </Text>
          <View style={styles.inlineRow}>
            <TextInput
              style={[styles.input, styles.inputMid]}
              value={restingBpm}
              onChangeText={setRestingBpm}
              keyboardType="number-pad"
              maxLength={3}
              accessibilityLabel="安静時心拍（bpm）"
              testID="resting-bpm-input"
            />
            <Text style={styles.unitText}>bpm</Text>
          </View>
          <Text style={styles.inputHint}>片方だけの入力でも保存できます。同じ日の記録は上書きされます。</Text>

          <TouchableOpacity
            style={[styles.saveButton, saving && styles.syncButtonDisabled]}
            onPress={handleManualSave}
            disabled={saving}
            testID="manual-save-button"
          >
            {saving ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.saveButtonText}>保存</Text>
            )}
          </TouchableOpacity>
        </View>

        {/* 機能見直し-1-#5: 体調の参考情報（最新値・直近4週間平均・平均との差） */}
        <Text style={styles.sectionTitle}>体調の参考情報</Text>
        {summary?.sleep ? (
          <ReferenceCard
            label="睡眠"
            source={sourceLabel(summary.sleep.source)}
            date={`${fmtDate(summary.sleep.date)}（起きた日）`}
            value={formatSleepMinutes(summary.sleep.durationMinutes)}
            lines={
              summary.sleep.averageMinutesPast4Weeks != null
                ? [
                    `直近4週間平均 ${formatSleepMinutes(summary.sleep.averageMinutesPast4Weeks)}（${summary.sleep.averageDaysPast4Weeks}日分の記録）`,
                    sleepDiffText(
                      summary.sleep.durationMinutes,
                      summary.sleep.averageMinutesPast4Weeks,
                    ),
                  ]
                : [NO_AVERAGE_TEXT]
            }
            testID="reference-sleep"
          />
        ) : (
          <Text style={styles.referenceEmpty}>睡眠の記録はまだありません</Text>
        )}
        {summary?.restingHeartRate ? (
          <ReferenceCard
            label="安静時心拍"
            source={sourceLabel(summary.restingHeartRate.source)}
            date={fmtDate(summary.restingHeartRate.date)}
            value={`${summary.restingHeartRate.restingBpm} bpm`}
            lines={
              summary.restingHeartRate.averageBpmPast4Weeks != null
                ? [
                    `直近4週間平均 ${summary.restingHeartRate.averageBpmPast4Weeks} bpm（${summary.restingHeartRate.averageDaysPast4Weeks}日分の記録）`,
                    restingBpmDiffText(
                      summary.restingHeartRate.restingBpm,
                      summary.restingHeartRate.averageBpmPast4Weeks,
                    ),
                  ]
                : [NO_AVERAGE_TEXT]
            }
            testID="reference-resting-hr"
          />
        ) : (
          <Text style={styles.referenceEmpty}>安静時心拍の記録はまだありません</Text>
        )}
        <Text style={styles.referenceNote}>{COMPARISON_NOTE}</Text>

        {!hasAnyData && (
          <Text style={styles.emptyText}>
            体重・歩数などの同期されたデータはまだありません。上のボタンから同期してください。
          </Text>
        )}

        {summary?.weight && (
          <SummaryCard
            icon="⚖️"
            label="体重"
            date={fmtDate(summary.weight.date)}
            value={`${summary.weight.weightKg} kg`}
            sub={summary.weight.bodyFatPct != null ? `体脂肪率 ${summary.weight.bodyFatPct}%` : undefined}
          />
        )}

        {summary?.steps && (
          <SummaryCard
            icon="👣"
            label="歩数"
            date={fmtDate(summary.steps.date)}
            value={`${summary.steps.stepCount.toLocaleString()} 歩`}
          />
        )}

        {summary?.heartRate && (
          <SummaryCard
            icon="❤️"
            label="心拍数"
            date={fmtDate(summary.heartRate.date)}
            value={summary.heartRate.avgBpm != null ? `平均 ${summary.heartRate.avgBpm} bpm` : '-'}
            sub={
              summary.heartRate.minBpm != null && summary.heartRate.maxBpm != null
                ? `${summary.heartRate.minBpm} 〜 ${summary.heartRate.maxBpm} bpm`
                : undefined
            }
          />
        )}

        {summary?.calories && (
          <SummaryCard
            icon="🔥"
            label="消費カロリー"
            date={fmtDate(summary.calories.date)}
            value={
              summary.calories.activeCalories != null
                ? `${Math.round(summary.calories.activeCalories)} kcal`
                : '-'
            }
          />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function ReferenceCard({
  label, source, date, value, lines, testID,
}: {
  label: string; source: string; date: string; value: string; lines: string[]; testID?: string;
}) {
  return (
    <View style={styles.card} testID={testID}>
      <View style={styles.referenceHeader}>
        <Text style={styles.cardLabel}>{label}</Text>
        <Text style={styles.sourceBadge}>{source}</Text>
      </View>
      <Text style={styles.cardDate}>{date}</Text>
      <Text style={styles.cardValue}>{value}</Text>
      {lines.map((l) => (
        <Text key={l} style={styles.referenceLine}>{l}</Text>
      ))}
    </View>
  );
}

function SummaryCard({
  icon, label, date, value, sub,
}: {
  icon: string; label: string; date: string; value: string; sub?: string;
}) {
  return (
    <View style={styles.card}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardIcon}>{icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardLabel}>{label}</Text>
          <Text style={styles.cardDate}>{date}</Text>
        </View>
      </View>
      <Text style={styles.cardValue}>{value}</Text>
      {sub && <Text style={styles.cardSub}>{sub}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backText: { fontSize: 15, color: '#4CAF50', width: 50 },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#222' },
  content: { padding: 16, paddingBottom: 40 },
  syncButton: {
    backgroundColor: '#4CAF50', borderRadius: 12, paddingVertical: 14,
    alignItems: 'center', marginBottom: 20,
  },
  syncButtonDisabled: { opacity: 0.6 },
  syncButtonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  emptyText: { textAlign: 'center', color: '#999', fontSize: 14, marginTop: 8, marginBottom: 12 },
  card: {
    backgroundColor: '#f9f9f9', borderRadius: 14, padding: 16, marginBottom: 12,
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  cardIcon: { fontSize: 24, marginRight: 10 },
  cardLabel: { fontSize: 14, color: '#666', fontWeight: '600' },
  cardDate: { fontSize: 12, color: '#767676' },
  cardValue: { fontSize: 22, fontWeight: '800', color: '#222' },
  cardSub: { fontSize: 13, color: '#888', marginTop: 4 },
  // 機能見直し-1-#5
  sectionTitle: { fontSize: 13, fontWeight: '700', color: '#555', marginTop: 4, marginBottom: 8 },
  inputLabel: { fontSize: 13, color: '#666', fontWeight: '600', marginBottom: 6 },
  inputLabelSpaced: { marginTop: 14 },
  inputHint: { fontSize: 12, color: '#767676', marginBottom: 6 },
  input: {
    height: 44, borderWidth: 1, borderColor: '#ddd', borderRadius: 8,
    paddingHorizontal: 12, fontSize: 15, backgroundColor: '#fff', color: '#222',
  },
  inputShort: { width: 64, textAlign: 'right' },
  inputMid: { width: 90, textAlign: 'right' },
  inlineRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  unitText: { fontSize: 14, marginLeft: 8, color: '#222' },
  minuteRow: { flexDirection: 'row', gap: 6, marginBottom: 6 },
  minuteButton: {
    flex: 1, height: 44, borderWidth: 1, borderColor: '#ddd', borderRadius: 8,
    alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff',
  },
  minuteButtonActive: { borderWidth: 2, borderColor: '#4CAF50', backgroundColor: '#e8f5e9' },
  minuteText: { fontSize: 14, color: '#333' },
  minuteTextActive: { color: '#2e7d32', fontWeight: '700' },
  saveButton: {
    backgroundColor: '#4CAF50', borderRadius: 10, height: 44,
    alignItems: 'center', justifyContent: 'center', marginTop: 10,
  },
  saveButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  referenceHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 2,
  },
  sourceBadge: {
    fontSize: 11, color: '#555', backgroundColor: '#eee', borderRadius: 10,
    paddingHorizontal: 8, paddingVertical: 2, overflow: 'hidden',
  },
  referenceLine: { fontSize: 13, color: '#555', marginTop: 4 },
  referenceEmpty: { fontSize: 13, color: '#767676', marginBottom: 12 },
  referenceNote: { fontSize: 11, color: '#767676', marginBottom: 16 },
});
