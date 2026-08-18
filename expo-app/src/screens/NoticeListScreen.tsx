import React, { useCallback, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, ScrollView, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { noticeApi } from '../api/client';
import type { Notice } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'NoticeList'>;
};

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString('ja-JP', {
    year: 'numeric', month: 'numeric', day: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function NoticeListScreen({ navigation }: Props) {
  const [notices, setNotices] = useState<Notice[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const { data } = await noticeApi.getActive();
      setNotices(data);
      // 一覧を開いて表示した時点で「閲覧済み」として全件dismissする
      // （既読管理は行わず、閲覧＝以後非表示という仕様のため）
      await Promise.all(data.map((n) => noticeApi.dismiss(n.id).catch(() => {})));
    } catch {
      // 取得失敗時は空表示のまま
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.backText}>{'< 戻る'}</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>お知らせ</Text>
        <View style={{ width: 50 }} />
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color="#4CAF50" />
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          {notices.length === 0 ? (
            <Text style={styles.emptyText}>現在お知らせはありません</Text>
          ) : (
            notices.map((notice) => (
              <View key={notice.id} style={styles.card}>
                <Text style={styles.cardTitle}>{notice.title}</Text>
                <Text style={styles.cardDate}>{fmtDateTime(notice.createdAt)}</Text>
                <Text style={styles.cardBody}>{notice.body}</Text>
              </View>
            ))
          )}
        </ScrollView>
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
  backText: { fontSize: 15, color: '#4CAF50' },
  headerTitle: { fontSize: 17, fontWeight: '700', color: '#222' },
  content: { padding: 16 },
  emptyText: { textAlign: 'center', color: '#999', marginTop: 48, fontSize: 14 },
  card: {
    backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 12,
    borderWidth: 1, borderColor: '#eee',
  },
  cardTitle: { fontSize: 15, fontWeight: '700', color: '#222', marginBottom: 4 },
  cardDate: { fontSize: 11, color: '#999', marginBottom: 8 },
  cardBody: { fontSize: 13, color: '#444', lineHeight: 20 },
});
