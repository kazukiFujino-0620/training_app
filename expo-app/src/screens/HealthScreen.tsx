import React, { useCallback, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, Alert, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { healthApi } from '../api/client';
import type { HealthSummaryResponse } from '../api/types';
import { syncHealthData } from '../health';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Health'>;
};

function fmtDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString('ja-JP', { month: 'long', day: 'numeric' });
}

function fmtTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString('ja-JP', { hour: '2-digit', minute: '2-digit' });
}

export default function HealthScreen({ navigation }: Props) {
  const [summary, setSummary] = useState<HealthSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);

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
    summary.weight || summary.steps || summary.heartRate || summary.calories || summary.sleep
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

      <ScrollView contentContainerStyle={styles.content}>
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

        {!hasAnyData && (
          <Text style={styles.emptyText}>
            まだ同期されたデータがありません。上のボタンから同期してください。
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

        {summary?.sleep && (
          <SummaryCard
            icon="😴"
            label="睡眠"
            date={fmtDate(summary.sleep.date)}
            value={`${Math.floor(summary.sleep.durationMinutes / 60)}時間${summary.sleep.durationMinutes % 60}分`}
            sub={`${fmtTime(summary.sleep.startTime)} 〜 ${fmtTime(summary.sleep.endTime)}`}
          />
        )}
      </ScrollView>
    </SafeAreaView>
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
  emptyText: { textAlign: 'center', color: '#999', fontSize: 14, marginTop: 24 },
  card: {
    backgroundColor: '#f9f9f9', borderRadius: 14, padding: 16, marginBottom: 12,
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  cardIcon: { fontSize: 24, marginRight: 10 },
  cardLabel: { fontSize: 14, color: '#666', fontWeight: '600' },
  cardDate: { fontSize: 12, color: '#aaa' },
  cardValue: { fontSize: 22, fontWeight: '800', color: '#222' },
  cardSub: { fontSize: 13, color: '#888', marginTop: 4 },
});
