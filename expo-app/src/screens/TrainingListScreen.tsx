import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity,
  Alert, ActivityIndicator, RefreshControl, AppState, Modal, Pressable,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { Calendar } from 'react-native-calendars';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import TrainingCard from '../components/TrainingCard';
import ProgressBar from '../components/ProgressBar';
import { trainingApi, noticeApi, coachingApi } from '../api/client';
import { clearTokens, getUserName } from '../auth/tokenStore';
import type { Training, AiTrainingSuggestion } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'TrainingList'>;
};

/** ローカル日付のYYYY-MM-DD文字列を返す（UTC変換によるズレを避けるためtoISOStringは使わない） */
function toDateString(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function todayDateString(): string {
  return toDateString(new Date());
}

function formatDateLabel(dateStr: string): string {
  const d = new Date(`${dateStr}T00:00:00`);
  return d.toLocaleDateString('ja-JP', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'short',
  });
}

export default function TrainingListScreen({ navigation }: Props) {
  const [date, setDate] = useState(todayDateString());
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [loading, setLoading]     = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [calories, setCalories] = useState<number | null>(null);
  const [noticeCount, setNoticeCount] = useState(0);
  const [aiSuggestion, setAiSuggestion] = useState<AiTrainingSuggestion | null>(null);
  const [userName, setUserName] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  // itバグ-18: ログインユーザー名をヘッダーに表示する
  useEffect(() => {
    getUserName().then(setUserName);
  }, []);

  const today = todayDateString();
  const isToday = date === today;
  const isPast = date < today;
  const isFuture = date > today;

  const load = useCallback(async () => {
    try {
      const { data } = await trainingApi.getToday(date);
      setTrainings(data);

      try {
        const { data: calorieData } = await trainingApi.getTodayCalories(date);
        setCalories(calorieData.available ? calorieData.calories : null);
      } catch {
        setCalories(null);
      }

      try {
        const { data: notices } = await noticeApi.getActive();
        setNoticeCount(notices.length);
      } catch {
        setNoticeCount(0);
      }

      // ita5-1 機能1（仮連携）: 当日のAIトレーニング提案（同意していない/提案が無い場合は204）。当日以外では表示しない
      if (isToday) {
        try {
          const { data: suggestion } = await coachingApi.getTodayTrainingSuggestion();
          setAiSuggestion(suggestion && suggestion.items?.length > 0 ? suggestion : null);
        } catch {
          setAiSuggestion(null);
        }
      } else {
        setAiSuggestion(null);
      }
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
  }, [navigation, date, isToday]);

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

  function handleSelectDate(day: { dateString: string }) {
    setDate(day.dateString);
    setCalendarOpen(false);
    setLoading(true);
  }

  const totalSets     = trainings.reduce((s, t) => s + t.details.length, 0);
  const completedSets = trainings.reduce(
    (s, t) => s + t.details.filter((d) => d.completed).length, 0,
  );
  // 全種目が完了済みのとき「▶ トレーニング開始」を非表示にする
  const isAllTrainingsCompleted = trainings.length > 0 && trainings.every((t) => t.allCompleted);

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
        <View style={styles.headerRow}>
          <TouchableOpacity
            style={styles.dateTouchable}
            onPress={() => setCalendarOpen(true)}
            accessibilityLabel="日付を選択"
          >
            <Text style={styles.dateText}>
              {formatDateLabel(date)} <Feather name="chevron-down" size={12} color="#888" />
            </Text>
            <Text style={styles.headerTitle}>
              {isToday ? '今日のトレーニング' : isPast ? '過去のトレーニング' : '予定のトレーニング'}
            </Text>
            {userName && <Text style={styles.userNameText}>ユーザー名: {userName}</Text>}
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => setMenuOpen(true)}
            style={styles.menuButton}
            accessibilityLabel="メニュー"
          >
            <Feather name="menu" size={22} color="#333" />
          </TouchableOpacity>
        </View>
      </View>

      {/* カレンダー（日付選択、ita7-1 1-1） */}
      <Modal
        visible={calendarOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setCalendarOpen(false)}
      >
        <Pressable style={styles.calendarBackdrop} onPress={() => setCalendarOpen(false)}>
          <Pressable style={styles.calendarCard} onPress={() => {}}>
            <Calendar
              current={date}
              onDayPress={handleSelectDate}
              markedDates={{ [date]: { selected: true, selectedColor: '#4CAF50' } }}
              theme={{
                todayTextColor: '#4CAF50',
                selectedDayBackgroundColor: '#4CAF50',
                arrowColor: '#4CAF50',
              }}
            />
            <TouchableOpacity
              style={styles.calendarTodayButton}
              onPress={() => handleSelectDate({ dateString: today })}
            >
              <Text style={styles.calendarTodayButtonText}>今日に戻る</Text>
            </TouchableOpacity>
          </Pressable>
        </Pressable>
      </Modal>

      {/* ヘッダーメニュー（お知らせ・体重記録・ヘルスケア・プロフィール・退会・ログアウト） */}
      <Modal visible={menuOpen} transparent animationType="fade" onRequestClose={() => setMenuOpen(false)}>
        <Pressable style={styles.menuBackdrop} onPress={() => setMenuOpen(false)}>
          <View style={styles.menuCard}>
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('NoticeList'); }}
            >
              <Text style={styles.menuItemText}>
                お知らせ{noticeCount > 0 ? `（${noticeCount}）` : ''}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('Health'); }}
            >
              <Text style={styles.menuItemText}>ヘルスケア</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('BodyMeasurement'); }}
            >
              <Text style={styles.menuItemText}>体重記録</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('Profile'); }}
            >
              <Text style={styles.menuItemText}>プロフィール</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.menuItem, styles.menuItemLast]}
              onPress={() => { setMenuOpen(false); navigation.navigate('Withdrawal'); }}
            >
              <Text style={styles.menuItemDangerText}>退会</Text>
            </TouchableOpacity>
            <View style={styles.menuGap} />
            <TouchableOpacity
              style={[styles.menuItem, styles.menuItemLast]}
              onPress={() => { setMenuOpen(false); handleLogout(); }}
            >
              <Text style={styles.menuItemMutedText}>ログアウト</Text>
            </TouchableOpacity>
          </View>
        </Pressable>
      </Modal>

      {/* お知らせバナー（ita2-5） */}
      {noticeCount > 0 && (
        <TouchableOpacity
          style={styles.noticeBanner}
          onPress={() => navigation.navigate('NoticeList')}
        >
          <Text style={styles.noticeBannerText}>
            お知らせがあります（{noticeCount}件）
          </Text>
          <Text style={styles.noticeBannerArrow}>確認する →</Text>
        </TouchableOpacity>
      )}

      {/* ita5-1 機能1（仮連携）: AIトレーニング提案（当日のみ） */}
      {aiSuggestion && (
        <View style={styles.aiSuggestionBanner}>
          <Text style={styles.aiSuggestionText} numberOfLines={2}>
            🤖 {aiSuggestion.comment}
          </Text>
          <TouchableOpacity
            style={styles.aiSuggestionButton}
            onPress={() => navigation.navigate('AddExercise', { aiSuggestion, date })}
          >
            <Text style={styles.aiSuggestionButtonText}>この提案を反映する</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* 全体プログレス */}
      {trainings.length > 0 && (
        <View style={styles.progressContainer}>
          <ProgressBar completed={completedSets} total={totalSets} />
        </View>
      )}

      {/* 消費カロリー（全種目完了時のみ表示） */}
      {calories !== null && (
        <View style={styles.calorieContainer}>
          <Text style={styles.calorieText}>消費カロリー：{calories} kcal</Text>
        </View>
      )}

      <FlatList
        data={trainings}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <TrainingCard
            training={item}
            onPress={() =>
              navigation.navigate('Exercise', { trainingId: item.id, menu: item.menu, date })
            }
            onDelete={() => handleDelete(item.id)}
          />
        )}
        ListEmptyComponent={
          <View style={styles.empty}>
            <Feather name="clipboard" size={48} color="#aaa" style={styles.emptyIcon} />
            <Text style={styles.emptyText}>
              {isToday ? '今日のトレーニングはありません' : 'この日のトレーニングはありません'}
            </Text>
            {!isPast && (
              <Text style={styles.emptySubText}>
                ＋ボタンから種目を追加してください
              </Text>
            )}
          </View>
        }
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} />
        }
      />

      {/* フッター */}
      {/* ita7-1 1-1: 過去日は編集・削除のみ対応のため、+追加・▶開始とも非表示にする */}
      {!isPast && (
        <View style={styles.footer}>
          {trainings.length > 0 && !isFuture && !isAllTrainingsCompleted ? (
            // 当日・未完了あり：両ボタンを表示
            <View style={styles.buttonRow}>
              <TouchableOpacity
                style={styles.addButtonOutline}
                onPress={() => navigation.navigate('AddExercise', { date })}
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
            // 当日で全種目完了済み、または未来日（種目の有無を問わずライブセッションは開始不可）：
            // 「種目追加」のみ全幅表示
            <TouchableOpacity
              style={styles.addButton}
              onPress={() => navigation.navigate('AddExercise', { date })}
            >
              <Text style={styles.addButtonText}>＋ 種目を追加</Text>
            </TouchableOpacity>
          )}
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    paddingHorizontal: 16, paddingVertical: 12, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  dateTouchable: { flexShrink: 1 },
  dateText: { fontSize: 12, color: '#888' },
  headerTitle: { fontSize: 20, fontWeight: '800', color: '#222' },
  userNameText: { fontSize: 12, color: '#666', marginTop: 2 },
  headerRow: {
    flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between',
  },
  menuButton: { padding: 6, marginTop: 2 },
  calendarBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'center', alignItems: 'center',
  },
  calendarCard: {
    width: '90%', backgroundColor: '#fff', borderRadius: 16, padding: 12,
    shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 16, shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },
  calendarTodayButton: {
    marginTop: 8, alignSelf: 'center', paddingVertical: 8, paddingHorizontal: 16,
  },
  calendarTodayButtonText: { color: '#4CAF50', fontWeight: '700', fontSize: 13 },
  menuBackdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.15)' },
  menuCard: {
    position: 'absolute', top: 68, right: 14, width: 200,
    backgroundColor: '#fff', borderRadius: 12, paddingVertical: 4,
    shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 12, shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  menuItem: {
    paddingHorizontal: 16, paddingVertical: 13,
    borderBottomWidth: 1, borderBottomColor: '#f2f2f2',
  },
  menuItemLast: { borderBottomWidth: 0 },
  menuItemText: { fontSize: 14, fontWeight: '600', color: '#333' },
  menuItemDangerText: { fontSize: 14, fontWeight: '600', color: '#e53935' },
  menuItemMutedText: { fontSize: 14, fontWeight: '600', color: '#888' },
  menuGap: { height: 8, backgroundColor: '#fafafa', borderTopWidth: 1, borderBottomWidth: 1, borderColor: '#f2f2f2' },
  noticeBanner: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginHorizontal: 16, marginTop: 12, padding: 12,
    backgroundColor: '#fff8e1', borderRadius: 10,
    borderLeftWidth: 4, borderLeftColor: '#f9a825',
  },
  noticeBannerText: { fontSize: 13, fontWeight: '700', color: '#222' },
  noticeBannerArrow: { fontSize: 12, color: '#999' },
  aiSuggestionBanner: {
    marginHorizontal: 16, marginTop: 12, padding: 12,
    backgroundColor: '#eef2ff', borderRadius: 10,
    borderLeftWidth: 4, borderLeftColor: '#6366f1',
  },
  aiSuggestionText: { fontSize: 13, color: '#333', marginBottom: 8 },
  aiSuggestionButton: {
    alignSelf: 'flex-start', backgroundColor: '#6366f1',
    borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6,
  },
  aiSuggestionButtonText: { fontSize: 12, color: '#fff', fontWeight: '700' },
  progressContainer: { paddingHorizontal: 16, paddingTop: 12 },
  calorieContainer: { paddingHorizontal: 16, paddingTop: 8 },
  calorieText: { fontSize: 13, color: '#666', fontWeight: '600' },
  list: { paddingTop: 4, paddingBottom: 16 },
  empty: { flex: 1, alignItems: 'center', paddingTop: 80 },
  emptyIcon: { marginBottom: 12 },
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
