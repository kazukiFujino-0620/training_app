import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity,
  Alert, ActivityIndicator, RefreshControl, AppState, Modal, Pressable, ScrollView,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { Calendar, DateData } from 'react-native-calendars';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import TrainingCard from '../components/TrainingCard';
import ProgressBar from '../components/ProgressBar';
import { trainingApi, noticeApi, recommendationApi, statsApi } from '../api/client';
import { clearTokens, getUserName } from '../auth/tokenStore';
import type { Training, DailyRecommendation, MobileTrainingStatsResponse } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'TrainingList'>;
};

type TabKey = 'calendar' | 'training';

/** カレンダーの日付セル単位の状態（ita7-3: 選択枠線・今日強調・実施日ドットを個別に見分けるため） */
type CalendarDayMarking = { selected?: boolean; isToday?: boolean; done?: boolean };

/**
 * カレンダーの日付セル。react-native-calendars の `dayComponent` として渡す。
 * ita7-3: 選択中の日付＝枠線、今日＝文字色強調（太字＋アクセントカラー）、実施日＝下にドット、を
 * 独立したスタイルとして重ね合わせて表示する（重なる場合は両方見える）。
 */
function CalendarDayCell({
  date,
  state,
  marking,
  onPress,
}: {
  date?: DateData;
  state?: string;
  marking?: CalendarDayMarking;
  onPress?: (date?: DateData) => void;
}) {
  if (!date) return null;
  const isSelected = !!marking?.selected;
  const isToday = !!marking?.isToday;
  const isDone = !!marking?.done;
  const isDisabled = state === 'disabled';

  return (
    <TouchableOpacity
      onPress={() => onPress?.(date)}
      disabled={isDisabled}
      style={styles.calDayCell}
      accessibilityLabel={`${date.dateString}${isDone ? '（トレーニング実施済み）' : ''}`}
    >
      <View style={[styles.calDayCircle, isSelected && styles.calDayCircleSelected]}>
        <Text
          style={[
            styles.calDayText,
            isDisabled && styles.calDayTextDisabled,
            isToday && styles.calDayTextToday,
          ]}
        >
          {date.day}
        </Text>
      </View>
      <View style={styles.calDayDotSlot}>{isDone && <View style={styles.calDayDot} />}</View>
    </TouchableOpacity>
  );
}

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
  // ita7-2: 「カレンダー」「トレーニング」の2タブ構成。アプリ起動時はまず月間の実施状況を
  // 把握できるよう「カレンダー」を初期表示にする（2026-09-10変更）
  const [activeTab, setActiveTab] = useState<TabKey>('calendar');
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [loading, setLoading]     = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  // ita7-3: カレンダータブの選択日プレビューカード用（フルスクリーンのloadingとは分離し、
  // カレンダー・統計バーを表示したまま概要部分だけ読み込み中にする）
  const [previewLoading, setPreviewLoading] = useState(false);
  const [calories, setCalories] = useState<number | null>(null);
  const [noticeCount, setNoticeCount] = useState(0);
  // itバグ-21対応（2026-09-11）: 「（モック）」文言のAI提案カードを廃止し、
  // 既存のルールベース推奨（RecommendationService）による「今日のおすすめメニュー」に差し替えた。
  const [dailyRecommendation, setDailyRecommendation] = useState<DailyRecommendation | null>(null);
  const [userName, setUserName] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  // ita7-2: カレンダータブ下の統計バー（Web版 /menu と同じ内容）
  const [stats, setStats] = useState<MobileTrainingStatsResponse | null>(null);

  // itバグ-18: ログインユーザー名をヘッダーに表示する
  useEffect(() => {
    getUserName().then(setUserName);
  }, []);

  const today = todayDateString();
  const isToday = date === today;
  const isPast = date < today;
  const isFuture = date > today;

  const load = useCallback(async () => {
    setPreviewLoading(true);
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

      // itバグ-21対応: 今日のおすすめメニュー（ルールベース推奨）。当日以外では表示しない
      if (isToday) {
        try {
          const { data: recommendation } = await recommendationApi.getToday();
          setDailyRecommendation(recommendation ?? null);
        } catch {
          setDailyRecommendation(null);
        }
      } else {
        setDailyRecommendation(null);
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
      setPreviewLoading(false);
    }
  }, [navigation, date, isToday]);

  // 画面フォーカス時に再取得
  useFocusEffect(useCallback(() => { load(); }, [load]));

  // ita7-2: カレンダータブ下の統計バー。選択中の日付に関わらず常に「今日時点」の集計のため、
  // 日付変更時ではなく画面フォーカス時のみ再取得する
  const loadStats = useCallback(async () => {
    try {
      const { data } = await statsApi.getTraining();
      setStats(data);
    } catch {
      setStats(null);
    }
  }, []);
  useFocusEffect(useCallback(() => { loadStats(); }, [loadStats]));

  // バックグラウンド→フォアグラウンド復帰時に再取得
  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') load();
    });
    return () => sub.remove();
  }, [load]);

  // ita7-3: カレンダーの日付セルごとの状態（選択枠線・今日強調・実施日ドット）をまとめる。
  // 実施日一覧は統計バーAPI（当月分）を流用し、DB変更・新規APIは追加しない
  const markedDates = useMemo(() => {
    const md: Record<string, CalendarDayMarking> = {};
    (stats?.trainingDates ?? []).forEach((d) => {
      md[d] = { ...(md[d] ?? {}), done: true };
    });
    md[date] = { ...(md[date] ?? {}), selected: true };
    md[today] = { ...(md[today] ?? {}), isToday: true };
    return md;
  }, [stats, date, today]);

  // ita7-3: 選択中の日付プレビューカードの概要テキスト。既存のトレーニング一覧取得API（日付指定）で
  // 取得済みの`trainings`をそのまま流用する（新規APIは追加しない）
  const previewSummary = useMemo(() => {
    if (trainings.length === 0) return '未実施';
    const menuNames = trainings.map((t) => t.menu).filter(Boolean);
    const partNames = Array.from(
      new Set(trainings.map((t) => t.partName).filter((p): p is string => !!p)),
    );
    const completedCount = trainings.filter((t) => t.allCompleted).length;
    const namesLabel = menuNames.join('・');
    const partsLabel = partNames.length > 0 ? `（${partNames.join('・')}）` : '';
    const statusLabel =
      completedCount === trainings.length
        ? `全${trainings.length}種目完了`
        : `${completedCount}/${trainings.length}種目完了`;
    return `${namesLabel}${partsLabel} ${statusLabel}`;
  }, [trainings]);

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

  // ita7-3: カレンダーで日付をタップしても、選択状態になるだけでタブ遷移はしない
  // （選択日の概要はカレンダー直下のプレビューカードに表示。「トレーニング」タブへは
  // プレビューカードの「この日のトレーニングを見る」ボタンからのみ遷移する）
  function handleSelectDate(day: { dateString: string }) {
    setDate(day.dateString);
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
            onPress={() => setActiveTab('calendar')}
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

      {/* ヘッダーメニュー（プロフィール・体重記録／お知らせ／ヘルスケア・退会／ログアウト、ita7-2で並び替え） */}
      <Modal visible={menuOpen} transparent animationType="fade" onRequestClose={() => setMenuOpen(false)}>
        <Pressable style={styles.menuBackdrop} onPress={() => setMenuOpen(false)}>
          <View style={styles.menuCard}>
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('Profile'); }}
            >
              <Text style={styles.menuItemText}>プロフィール</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.menuItem, styles.menuItemLast]}
              onPress={() => { setMenuOpen(false); navigation.navigate('BodyMeasurement'); }}
            >
              <Text style={styles.menuItemText}>体重記録</Text>
            </TouchableOpacity>
            <View style={styles.menuGap} />
            <TouchableOpacity
              style={[styles.menuItem, styles.menuItemLast]}
              onPress={() => { setMenuOpen(false); navigation.navigate('NoticeList'); }}
            >
              <Text style={styles.menuItemText}>
                お知らせ{noticeCount > 0 ? `（${noticeCount}）` : ''}
              </Text>
            </TouchableOpacity>
            <View style={styles.menuGap} />
            <TouchableOpacity
              style={styles.menuItem}
              onPress={() => { setMenuOpen(false); navigation.navigate('Health'); }}
            >
              <Text style={styles.menuItemText}>ヘルスケア</Text>
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

      {/* タブ（ita7-2: カレンダー／トレーニングの2タブ構成） */}
      <View style={styles.tabBar}>
        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'calendar' && styles.tabItemActive]}
          onPress={() => setActiveTab('calendar')}
        >
          <Text style={[styles.tabItemText, activeTab === 'calendar' && styles.tabItemTextActive]}>
            カレンダー
          </Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tabItem, activeTab === 'training' && styles.tabItemActive]}
          onPress={() => setActiveTab('training')}
        >
          <Text style={[styles.tabItemText, activeTab === 'training' && styles.tabItemTextActive]}>
            トレーニング
          </Text>
        </TouchableOpacity>
      </View>

      {activeTab === 'calendar' ? (
        <ScrollView contentContainerStyle={styles.calendarTabContent}>
          {/* 1ヶ月フル表示のカレンダー（ita7-1で追加した実装をタブ内に配置） */}
          <View style={styles.calendarCardInline}>
            <Calendar
              current={date}
              onDayPress={handleSelectDate}
              markedDates={markedDates}
              dayComponent={CalendarDayCell}
              theme={{ arrowColor: '#4CAF50' }}
            />
            <TouchableOpacity
              style={styles.calendarTodayButton}
              onPress={() => handleSelectDate({ dateString: today })}
            >
              <Text style={styles.calendarTodayButtonText}>今日に戻る</Text>
            </TouchableOpacity>
          </View>

          {/* 選択日プレビューカード（ita7-3新規）: タップでは遷移せず、ここに概要を表示する。
              「この日のトレーニングを見る」ボタンからのみ「トレーニング」タブへ遷移する */}
          <View style={styles.previewCard}>
            <Text style={styles.previewDate}>{formatDateLabel(date)}</Text>
            {previewLoading ? (
              <ActivityIndicator size="small" color="#4CAF50" style={styles.previewLoading} />
            ) : (
              <Text style={styles.previewSummary}>{previewSummary}</Text>
            )}
            <TouchableOpacity
              style={styles.previewButton}
              onPress={() => setActiveTab('training')}
            >
              <Text style={styles.previewButtonText}>この日のトレーニングを見る</Text>
            </TouchableOpacity>
          </View>

          {/* 統計バー（ita7-2新規、Web版 /menu と同じ内容） */}
          <View style={styles.statsRow}>
            <View style={styles.statCard}>
              <Text style={styles.statLabel}>今月</Text>
              <Text style={styles.statValue}>{stats ? `${stats.monthlyCount}回` : '-'}</Text>
            </View>
            <View style={styles.statCard}>
              <Text style={styles.statLabel}>先週比</Text>
              <Text
                style={[
                  styles.statValue,
                  stats
                    ? stats.volumeChangePositive
                      ? styles.statValuePositive
                      : styles.statValueNegative
                    : null,
                ]}
              >
                {stats ? stats.volumeChangeText : '-'}
              </Text>
            </View>
            {stats?.todayPartLabel != null && (
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>今日の予定</Text>
                <Text style={styles.statValue}>{stats.todayPartLabel}</Text>
              </View>
            )}
          </View>
          {stats && (
            <View style={styles.weekPartsCard}>
              <Text style={styles.statLabel}>今週の部位</Text>
              <View style={styles.weekPartsRow}>
                {stats.weekParts.map((p) => (
                  <View
                    key={p.name}
                    style={[styles.partBadge, p.done ? styles.partBadgeDone : styles.partBadgeUndone]}
                  >
                    <Text
                      style={[
                        styles.partBadgeText,
                        p.done ? styles.partBadgeTextDone : styles.partBadgeTextUndone,
                      ]}
                    >
                      {p.name}{p.done ? '✓' : '✗'}
                    </Text>
                  </View>
                ))}
              </View>
            </View>
          )}
        </ScrollView>
      ) : (
        <>
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

          {/* itバグ-21対応: 今日のおすすめメニュー（ルールベース推奨、当日のみ） */}
          {dailyRecommendation && dailyRecommendation.restDayRecommended && (
            <View style={styles.recommendationBanner}>
              <View style={styles.recommendationHeaderRow}>
                <Feather name="trending-up" size={14} color="#4f46e5" />
                <Text style={styles.recommendationLabel}>今日のおすすめメニュー</Text>
              </View>
              <Text style={styles.recommendationText}>
                今日は軽めの有酸素や休養日がおすすめです。全部位がまだ疲労中です。
              </Text>
            </View>
          )}
          {dailyRecommendation &&
            !dailyRecommendation.restDayRecommended &&
            dailyRecommendation.items.length > 0 && (
              <View style={styles.recommendationBanner}>
                <View style={styles.recommendationHeaderRow}>
                  <Feather name="trending-up" size={14} color="#4f46e5" />
                  <Text style={styles.recommendationLabel}>今日のおすすめメニュー</Text>
                </View>
                <Text style={styles.recommendationText} numberOfLines={2}>
                  {dailyRecommendation.reasonLabel}
                </Text>
                <TouchableOpacity
                  style={styles.recommendationButton}
                  onPress={() =>
                    navigation.navigate('AddExercise', {
                      aiSuggestion: { items: dailyRecommendation.items },
                      date,
                    })
                  }
                >
                  <Text style={styles.recommendationButtonText}>このメニューを反映する</Text>
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
        </>
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
  // ita7-2: 「カレンダー」「トレーニング」タブ
  tabBar: {
    flexDirection: 'row', backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  tabItem: {
    flex: 1, alignItems: 'center', paddingVertical: 12,
    borderBottomWidth: 2.5, borderBottomColor: 'transparent',
  },
  tabItemActive: { borderBottomColor: '#4CAF50' },
  tabItemText: { fontSize: 14, fontWeight: '700', color: '#999' },
  tabItemTextActive: { color: '#4CAF50' },
  calendarTabContent: { paddingBottom: 24 },
  calendarCardInline: {
    margin: 14, backgroundColor: '#fff', borderRadius: 16, padding: 12,
    shadowColor: '#000', shadowOpacity: 0.06, shadowRadius: 8, shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  calendarTodayButton: {
    marginTop: 8, alignSelf: 'center', paddingVertical: 8, paddingHorizontal: 16,
  },
  calendarTodayButtonText: { color: '#4CAF50', fontWeight: '700', fontSize: 13 },
  // ita7-3: カレンダーの日付セル（選択枠線・今日強調・実施日ドットを個別に重ね合わせる）
  calDayCell: { alignItems: 'center', justifyContent: 'center', width: 32, height: 40 },
  calDayCircle: {
    width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center',
    borderWidth: 1.5, borderColor: 'transparent',
  },
  calDayCircleSelected: { borderColor: '#4CAF50' },
  calDayText: { fontSize: 14, color: '#2d2d2d' },
  calDayTextDisabled: { color: '#d5d5d5' },
  calDayTextToday: { color: '#4CAF50', fontWeight: '800' },
  calDayDotSlot: { height: 8, alignItems: 'center', justifyContent: 'center' },
  calDayDot: { width: 5, height: 5, borderRadius: 2.5, backgroundColor: '#4CAF50' },
  // ita7-3: 選択日プレビューカード（カレンダー直下・統計バーより上）
  previewCard: {
    marginHorizontal: 14, marginTop: 4, marginBottom: 10, backgroundColor: '#fff', borderRadius: 14, padding: 14,
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 6, shadowOffset: { width: 0, height: 2 },
    elevation: 1,
  },
  previewDate: { fontSize: 13, color: '#888', fontWeight: '700', marginBottom: 6 },
  previewSummary: { fontSize: 15, color: '#222', fontWeight: '600', lineHeight: 21 },
  previewLoading: { alignSelf: 'flex-start', marginVertical: 2 },
  previewButton: {
    alignSelf: 'flex-start', marginTop: 10, paddingVertical: 8, paddingHorizontal: 14,
    borderRadius: 10, backgroundColor: '#e6f4ec',
  },
  previewButtonText: { color: '#2e8b52', fontWeight: '700', fontSize: 13 },
  // ita7-2: 統計バー（今月・先週比・今日の予定・今週の部位）
  statsRow: { flexDirection: 'row', gap: 8, marginHorizontal: 14 },
  statCard: {
    flex: 1, backgroundColor: '#fff', borderRadius: 10, padding: 10,
  },
  statLabel: { fontSize: 11, color: '#999', marginBottom: 4 },
  statValue: { fontSize: 16, fontWeight: '800', color: '#333' },
  statValuePositive: { color: '#22c55e' },
  statValueNegative: { color: '#ef4444' },
  weekPartsCard: {
    marginTop: 8, marginHorizontal: 14, backgroundColor: '#fff', borderRadius: 10, padding: 10,
  },
  weekPartsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 4 },
  partBadge: { borderRadius: 999, paddingHorizontal: 9, paddingVertical: 4 },
  partBadgeDone: { backgroundColor: '#e6f4ec' },
  partBadgeUndone: { backgroundColor: '#f0f0f0' },
  partBadgeText: { fontSize: 11.5, fontWeight: '700' },
  partBadgeTextDone: { color: '#1f8a4c' },
  partBadgeTextUndone: { color: '#999' },
  noticeBanner: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginHorizontal: 16, marginTop: 12, padding: 12,
    backgroundColor: '#fff8e1', borderRadius: 10,
    borderLeftWidth: 4, borderLeftColor: '#f9a825',
  },
  noticeBannerText: { fontSize: 13, fontWeight: '700', color: '#222' },
  noticeBannerArrow: { fontSize: 12, color: '#999' },
  recommendationBanner: {
    marginHorizontal: 16, marginTop: 12, padding: 12,
    backgroundColor: '#eef2ff', borderRadius: 10,
    borderLeftWidth: 4, borderLeftColor: '#4f46e5',
  },
  recommendationHeaderRow: {
    flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 4,
  },
  recommendationLabel: { fontSize: 12, color: '#4f46e5', fontWeight: '700' },
  recommendationText: { fontSize: 13, color: '#333', marginBottom: 8 },
  recommendationButton: {
    alignSelf: 'flex-start', backgroundColor: '#4f46e5',
    borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6,
  },
  recommendationButtonText: { fontSize: 12, color: '#fff', fontWeight: '700' },
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
