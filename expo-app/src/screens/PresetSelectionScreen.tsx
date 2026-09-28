import React, { useCallback, useMemo, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { periodizationApi } from '../api/client';
import type { PeriodizationPreset, PeriodizationProposal } from '../api/types';
import { errorMessage } from '../components/PeriodizationShared';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'PresetSelection'>;
  route: RouteProp<AppStackParamList, 'PresetSelection'>;
};

/**
 * プリセット選択（機能見直し-1-#3、モックアップ版11の01のモバイル版）。
 * renew=true のときはサイクル終了後の「別のプリセットを選ぶ」（renew API）として動く。
 */
export default function PresetSelectionScreen({ navigation, route }: Props) {
  const renew = !!route.params?.renew;
  const [presets, setPresets] = useState<PeriodizationPreset[]>([]);
  const [scheduled, setScheduled] = useState<PeriodizationProposal | null>(null);
  const [selected, setSelected] = useState<PeriodizationPreset | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);

  useFocusEffect(useCallback(() => {
    (async () => {
      try {
        const [{ data: p }, { data: prop }] = await Promise.all([
          periodizationApi.presets(), periodizationApi.proposals(),
        ]);
        setPresets(p);
        setScheduled(prop.scheduled[0] ?? null);
      } catch (e) {
        setError(errorMessage(e, 'プリセットを読み込めませんでした'));
      } finally {
        setLoading(false);
      }
    })();
  }, []));

  const groups = useMemo(() => {
    const map = new Map<string, PeriodizationPreset[]>();
    presets.forEach((p) => {
      const list = map.get(p.purposeLabel) ?? [];
      list.push(p);
      map.set(p.purposeLabel, list);
    });
    return Array.from(map.entries());
  }, [presets]);

  const start = async () => {
    if (!selected) return;
    try {
      if (renew) await periodizationApi.renew('CHOOSE_NEW_PRESET', selected.id);
      else await periodizationApi.adopt(selected.id);
      navigation.replace('ProgramCycle');
    } catch (e) {
      setConfirming(false);
      setError(errorMessage(e));
    }
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top', 'bottom']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< 戻る'}</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{renew ? '次のプログラムを選ぶ' : 'プログラムを選ぶ'}</Text>
        <View style={{ width: 50 }} />
      </View>
      {loading ? (
        <View style={styles.center}><ActivityIndicator size="large" color="#4CAF50" /></View>
      ) : (
        <>
          <ScrollView contentContainerStyle={styles.content}>
            {error && <Text style={styles.error}>{error}</Text>}
            {presets.length === 0 && (
              <Text style={styles.empty}>選べるプリセットがまだありません。下の「自分で組む」から始められます。</Text>
            )}
            {groups.map(([label, list]) => (
              <View key={label}>
                <Text style={styles.cat}>{label}</Text>
                {list.map((p) => (
                  <TouchableOpacity
                    key={p.id}
                    style={[styles.preset, selected?.id === p.id && styles.presetSelected]}
                    onPress={() => { setSelected(p); setConfirming(false); }}
                  >
                    <Text style={styles.presetName}>{p.name}</Text>
                    <Text style={styles.presetMeta}>{p.totalWeeks}週構成{p.description ? `・${p.description}` : ''}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            ))}
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
                  <TouchableOpacity style={[styles.btn, styles.btnPrimary, { flex: 1 }]} onPress={start}>
                    <Text style={styles.btnPrimaryText}>取り消して切り替える</Text>
                  </TouchableOpacity>
                </View>
              </View>
            )}
          </ScrollView>
          <View style={styles.footer}>
            <TouchableOpacity
              style={[styles.btn, styles.btnPrimary, !selected && styles.btnDisabled]}
              disabled={!selected}
              onPress={() => (scheduled ? setConfirming(true) : start())}
            >
              <Text style={styles.btnPrimaryText}>{selected ? 'このプログラムを始める' : 'プログラムを選んでください'}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.btn, styles.btnGhost, { marginTop: 8 }]}
              onPress={() => navigation.navigate('CustomCycleBuilder', {})}
            >
              <Text style={styles.btnGhostText}>自分で組む</Text>
            </TouchableOpacity>
          </View>
        </>
      )}
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
  error: { backgroundColor: '#fde8e8', color: '#b91c1c', padding: 10, borderRadius: 8, fontSize: 13, marginBottom: 10 },
  empty: { fontSize: 13, color: '#999', paddingVertical: 12 },
  cat: { fontSize: 11, color: '#999', letterSpacing: 0.5, marginTop: 14, marginBottom: 6 },
  preset: { backgroundColor: '#fff', borderRadius: 10, borderWidth: 1, borderColor: '#e0e0e0', padding: 14, marginBottom: 8 },
  presetSelected: { borderColor: '#4CAF50', backgroundColor: '#e8f5e9' },
  presetName: { fontSize: 14, fontWeight: '700', color: '#222' },
  presetMeta: { fontSize: 12, color: '#888', marginTop: 3 },
  confirm: { backgroundColor: '#fef3e2', borderWidth: 1.5, borderColor: '#f59e0b', borderRadius: 10, padding: 12, marginTop: 12 },
  confirmText: { fontSize: 13, color: '#333', lineHeight: 20 },
  actions: { flexDirection: 'row', gap: 8, marginTop: 10 },
  footer: { padding: 14, backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#eee' },
  btn: { borderRadius: 10, paddingVertical: 13, alignItems: 'center' },
  btnPrimary: { backgroundColor: '#4CAF50' },
  btnPrimaryText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  btnDisabled: { backgroundColor: '#a5d6a7' },
  btnGhost: { borderWidth: 1, borderColor: '#4CAF50', backgroundColor: '#fff' },
  btnGhostText: { color: '#4CAF50', fontWeight: '700', fontSize: 14 },
});
