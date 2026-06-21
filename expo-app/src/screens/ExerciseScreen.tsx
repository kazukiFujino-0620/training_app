import React, { useState, useEffect } from 'react';
import {
  View, Text, FlatList, StyleSheet, ActivityIndicator,
  Alert, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import SetRow from '../components/SetRow';
import { trainingApi } from '../api/client';
import type { Training, TrainingDetail } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Exercise'>;
  route: RouteProp<AppStackParamList, 'Exercise'>;
};

const PART_LABELS: Record<string, string> = {
  CHEST: '胸', BACK: '背中', SHOULDER: '肩',
  ARM: '腕', LEG: '脚',
};

export default function ExerciseScreen({ navigation, route }: Props) {
  const { trainingId, menu } = route.params;
  const [training, setTraining] = useState<Training | null>(null);
  const [loading, setLoading]   = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await trainingApi.getToday();
        const found = data.find((t) => t.id === trainingId) ?? null;
        setTraining(found);
      } catch {
        Alert.alert('エラー', 'データ取得に失敗しました');
      } finally {
        setLoading(false);
      }
    })();
  }, [trainingId]);

  function handleDetailUpdated(updated: TrainingDetail) {
    setTraining((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        details: prev.details.map((d) => (d.id === updated.id ? updated : d)),
      };
    });
  }

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </View>
    );
  }

  if (!training) {
    return (
      <View style={styles.center}>
        <Text style={styles.errorText}>種目が見つかりません</Text>
      </View>
    );
  }

  const completed = training.details.filter((d) => d.completed).length;
  const total = training.details.length;

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      {/* 種目ヘッダー */}
      <View style={styles.info}>
        <Text style={styles.partBadge}>
          {PART_LABELS[training.partCode] ?? training.partCode}
        </Text>
        <Text style={styles.menu}>{training.menu}</Text>
        <Text style={styles.progress}>{completed} / {total} セット完了</Text>
      </View>

      {/* セット行ヘッダー */}
      <View style={styles.tableHeader}>
        <Text style={[styles.colLabel, { width: 48 }]}>セット</Text>
        <Text style={[styles.colLabel, { flex: 1 }]}>重量</Text>
        <Text style={[styles.colLabel, { flex: 1 }]}>回数</Text>
        <Text style={[styles.colLabel, { width: 40 }]}>完了</Text>
      </View>

      <FlatList
        data={training.details}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <SetRow detail={item} onUpdated={handleDetailUpdated} />
        )}
        contentContainerStyle={styles.list}
        ListEmptyComponent={
          <Text style={styles.emptyText}>セットがありません</Text>
        }
      />

      <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
        <Text style={styles.backButtonText}>← 一覧に戻る</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  errorText: { color: '#666', fontSize: 16 },
  info: {
    backgroundColor: '#fff', padding: 20,
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  partBadge: {
    alignSelf: 'flex-start', fontSize: 12, color: '#4CAF50',
    backgroundColor: '#e8f5e9', paddingHorizontal: 10, paddingVertical: 3,
    borderRadius: 10, fontWeight: '600', marginBottom: 6,
  },
  menu: { fontSize: 22, fontWeight: '800', color: '#222', marginBottom: 4 },
  progress: { fontSize: 13, color: '#888' },
  tableHeader: {
    flexDirection: 'row', paddingHorizontal: 20, paddingVertical: 8,
    backgroundColor: '#f9f9f9', borderBottomWidth: 1, borderBottomColor: '#eee',
    gap: 8,
  },
  colLabel: { fontSize: 11, color: '#aaa', fontWeight: '600', textAlign: 'center' },
  list: { paddingHorizontal: 16, paddingTop: 4 },
  emptyText: { textAlign: 'center', color: '#aaa', padding: 32 },
  backButton: {
    margin: 16, padding: 14, backgroundColor: '#fff',
    borderRadius: 10, borderWidth: 1, borderColor: '#e0e0e0',
    alignItems: 'center',
  },
  backButtonText: { color: '#555', fontSize: 15, fontWeight: '600' },
});
