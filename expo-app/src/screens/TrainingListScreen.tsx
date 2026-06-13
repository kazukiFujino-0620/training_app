import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity,
  Alert, ActivityIndicator, RefreshControl, AppState,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import TrainingCard from '../components/TrainingCard';
import ProgressBar from '../components/ProgressBar';
import { trainingApi } from '../api/client';
import { clearTokens } from '../auth/tokenStore';
import type { Training } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'TrainingList'>;
};

export default function TrainingListScreen({ navigation }: Props) {
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [loading, setLoading]     = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const today = new Date().toLocaleDateString('ja-JP', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
  });

  const load = useCallback(async () => {
    try {
      const { data } = await trainingApi.getToday();
      setTrainings(data.filter((t) => !t.isAllCompleted));
    } catch (e: any) {
      if (e.response?.status === 401) {
        await clearTokens();
        navigation.replace('Auth' as any);
      } else {
        Alert.alert('エラー', 'トレーニングデータの取得に失敗しました');
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [navigation]);

  // 画面フォーカス時に再取得
  useFocusEffect(useCallback(() => { load(); }, [load]));

  // バックグラウンド→フォアグラウンド復帰時に再取得
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') load();
    });
    return () => sub.remove();
  }, [load]);

  async function handleDelete(id: number) {
    Alert.alert('種目を削除', 'この種目を削除しますか？', [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '削除', style: 'destructive',
        onPress: async () => {
          try {
            await trainingApi.deleteTraining(id);
            setTrainings((prev) => prev.filter((t) => t.id !== id));
          } catch {
            Alert.alert('エラー', '削除に失敗しました');
          }
        },
      },
    ]);
  }

  async function handleLogout() {
    await clearTokens();
    navigation.replace('Auth' as any);
  }

  const totalSets     = trainings.reduce((s, t) => s + t.details.length, 0);
  const completedSets = trainings.reduce(
    (s, t) => s + t.details.filter((d) => d.completed).length, 0,
  );

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* ヘッダー */}
      <View style={styles.header}>
        <View>
          <Text style={styles.dateText}>{today}</Text>
          <Text style={styles.headerTitle}>今日のトレーニング</Text>
        </View>
        <TouchableOpacity onPress={handleLogout}>
          <Text style={styles.logoutText}>ログアウト</Text>
        </TouchableOpacity>
      </View>

      {/* 全体プログレス */}
      {trainings.length > 0 && (
        <View style={styles.progressContainer}>
          <ProgressBar completed={completedSets} total={totalSets} />
        </View>
      )}

      <FlatList
        data={trainings}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <TrainingCard
            training={item}
            onPress={() =>
              navigation.navigate('Exercise', { trainingId: item.id, menu: item.menu })
            }
            onDelete={() => handleDelete(item.id)}
          />
        )}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyIcon}>📋</Text>
            <Text style={styles.emptyText}>今日のトレーニングはありません</Text>
            <Text style={styles.emptySubText}>
              ＋ボタンから種目を追加してください
            </Text>
          </View>
        }
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />
        }
      />

      {/* フッター */}
      <View style={styles.footer}>
        {trainings.length > 0 ? (
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.addButtonOutline}
              onPress={() => navigation.navigate('AddExercise')}
            >
              <Text style={styles.addButtonOutlineText}>＋ 種目を追加</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.startButton}
              onPress={() => navigation.navigate('TrainingStart')}
            >
              <Text style={styles.startButtonText}>▶ トレーニング開始</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <TouchableOpacity
            style={styles.addButton}
            onPress={() => navigation.navigate('AddExercise')}
          >
            <Text style={styles.addButtonText}>＋ 種目を追加</Text>
          </TouchableOpacity>
        )}
      </View>
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
  dateText: { fontSize: 12, color: '#888' },
  headerTitle: { fontSize: 20, fontWeight: '800', color: '#222' },
  logoutText: { fontSize: 13, color: '#999' },
  progressContainer: { paddingHorizontal: 16, paddingTop: 12 },
  list: { paddingTop: 4, paddingBottom: 16 },
  empty: { flex: 1, alignItems: 'center', paddingTop: 80 },
  emptyIcon: { fontSize: 48, marginBottom: 12 },
  emptyText: { fontSize: 16, color: '#666', fontWeight: '600', marginBottom: 4 },
  emptySubText: { fontSize: 13, color: '#aaa' },
  footer: {
    padding: 16, backgroundColor: '#fff',
    borderTopWidth: 1, borderTopColor: '#eee',
  },
  addButton: {
    backgroundColor: '#4CAF50', borderRadius: 12, padding: 16, alignItems: 'center',
  },
  addButtonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  buttonRow: { flexDirection: 'row', gap: 10 },
  addButtonOutline: {
    flex: 1, borderRadius: 12, padding: 16, alignItems: 'center',
    borderWidth: 1.5, borderColor: '#4CAF50', backgroundColor: '#fff',
  },
  addButtonOutlineText: { color: '#4CAF50', fontSize: 15, fontWeight: '700' },
  startButton: {
    flex: 1, backgroundColor: '#4CAF50', borderRadius: 12, padding: 16, alignItems: 'center',
  },
  startButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
