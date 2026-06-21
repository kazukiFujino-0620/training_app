import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, Vibration,
  SectionList, Alert, ActivityIndicator, AppState, AppStateStatus, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, UNSTABLE_usePreventRemove } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AppStackParamList } from '../navigation/AppNavigator';
import SetRow from '../components/SetRow';
import { trainingApi } from '../api/client';
import { clearTokens } from '../auth/tokenStore';
import type { Training, TrainingDetail } from '../api/types';
import * as Notifications from 'expo-notifications';
import { Audio } from 'expo-av';

const DEFAULT_INTERVAL = 120;

// アプリセッション内でコンポーネントが再マウントされてもタイマーを保持する
let _savedSessionStartTime: number | null = null;
let _savedSessionDate: string | null = null;

function todayDateStr(): string {
  return new Date().toISOString().slice(0, 10);
}

function isSessionRestorable(): boolean {
  return _savedSessionStartTime !== null && _savedSessionDate === todayDateStr();
}

// DBのduration文字列（"HH:MM:SS" または 秒数文字列）を秒数に変換
function parseDurationSec(duration?: string): number {
  if (!duration) return 0;
  if (duration.includes(':')) {
    const parts = duration.split(':').map(Number);
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    return 0;
  }
  const n = parseInt(duration, 10);
  return isNaN(n) ? 0 : n;
}

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'TrainingStart'>;
};

type TrainingSection = {
  key: string;
  trainingId: number;
  title: string;
  partCode: string;
  data: TrainingDetail[];
};

const PART_LABELS: Record<string, string> = {
  CHEST: '胸', BACK: '背中', SHOULDER: '肩',
  ARM: '腕', LEG: '脚',
};

function fmtTime(sec: number) {
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = sec % 60;
  if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export default function TrainingStartScreen({ navigation }: Props) {
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [loading, setLoading]     = useState(true);

  // セッションタイマー（カウントアップ）
  // 再マウント後も同日のタイマーを復元する
  const [sessionElapsed, setSessionElapsed] = useState(() =>
    isSessionRestorable()
      ? Math.floor((Date.now() - _savedSessionStartTime!) / 1000)
      : 0
  );
  const sessionStartRef = useRef<number | null>(isSessionRestorable() ? _savedSessionStartTime : null);
  const [sessionStarted, setSessionStarted] = useState(isSessionRestorable());
  const sessionStartedRef = useRef(isSessionRestorable());

  // 完了ナビゲーション時は中断ガードを素通りさせるフラグ
  // usePreventRemove の preventRemove 引数に渡すため state 化する（ref だと変更が伝わらない）
  const [isCompleting, setIsCompleting] = useState(false);
  // 中断確定時は中断ガードを素通りさせるフラグ（同上）
  const [isInterrupting, setIsInterrupting] = useState(false);

  // インターバルタイマー
  const [intervalDuration, setIntervalDuration] = useState(DEFAULT_INTERVAL);
  const [intervalRemaining, setIntervalRemaining] = useState<number | null>(null);
  const [intervalRunning, setIntervalRunning]   = useState(false);
  const [showInterval, setShowInterval]         = useState(false);
  const intervalDurationRef = useRef(DEFAULT_INTERVAL);
  const intervalStartRef    = useRef<number>(0);

  // バックグラウンド通知用
  const notificationIdRef = useRef<string | null>(null);
  const appStateRef = useRef<AppStateStatus>(AppState.currentState);
  // expo-av 音声: silence = バックグラウンドオーディオセッション維持用, alarm = アラーム音
  const silenceSoundRef = useRef<Audio.Sound | null>(null);
  const alarmSoundRef   = useRef<Audio.Sound | null>(null);

  // ── データ取得 ──────────────────────────────────────────────────────────────
  const load = useCallback(async (): Promise<Training[] | undefined> => {
    try {
      const { data } = await trainingApi.getToday();
      setTrainings(data);
      return data;
    } catch (e: any) {
      if (e.response?.status === 401) {
        await clearTokens();
        navigation.replace('Auth' as any);
      } else {
        Alert.alert('エラー', 'データ取得に失敗しました');
      }
      return undefined;
    } finally {
      setLoading(false);
    }
  }, [navigation]);

  useFocusEffect(useCallback(() => {
    (async () => {
      const data = await load();
      if (!data) return;

      if (isSessionRestorable() && !sessionStartedRef.current) {
        // 同セッション内でフォーカス復帰した場合（navigate で再マウントしない場合）
        const now = Date.now();
        sessionStartRef.current = _savedSessionStartTime;
        sessionStartedRef.current = true;
        setSessionStarted(true);
        setSessionElapsed(Math.floor((now - _savedSessionStartTime!) / 1000));
      } else if (!isSessionRestorable() && !sessionStartedRef.current) {
        // アプリ再起動後など：DBの duration からタイマーを復元
        const saved = Math.max(0, ...data.map((t) => parseDurationSec(t.duration)));
        if (saved > 0) {
          const restoredStart = Date.now() - saved * 1000;
          _savedSessionStartTime = restoredStart;
          _savedSessionDate = todayDateStr();
          sessionStartRef.current = restoredStart;
          sessionStartedRef.current = true;
          setSessionStarted(true);
          setSessionElapsed(saved);
        }
      }
    })();
  }, [load]));

  // 誤操作によるホーム遷移防止
  // - trainings が空・完了確定・中断確定 の場合のみ素通り
  // - native-stack ではヘッダー戻るボタン経由の遷移でネイティブ側が先に画面を pop してしまい、
  //   beforeRemove + e.preventDefault() では遷移を止められない（公式の既知の制限）。
  //   そのため usePreventRemove フックに置き換える。
  // - navigation.dispatch(data.action) は preventRemove を再評価させるため
  //   isInterrupting state で二重アラートを防ぐ
  const shouldPreventRemove = trainings.length > 0 && !isCompleting && !isInterrupting;
  UNSTABLE_usePreventRemove(shouldPreventRemove, ({ data }) => {
    Alert.alert(
      'トレーニングを中断しますか？',
      'ホームに戻るとトレーニングが中断されます。',
      [
        { text: 'キャンセル', style: 'cancel' },
        {
          text: '中断する',
          style: 'destructive',
          onPress: () => {
            setIsInterrupting(true);
            navigation.dispatch(data.action);
          },
        },
      ],
    );
  });

  // 通知パーミッションリクエスト
  useEffect(() => {
    Notifications.requestPermissionsAsync();
  }, []);

  // 画面離脱時に音声リソースを解放
  useEffect(() => {
    return () => {
      silenceSoundRef.current?.stopAsync().catch(() => {});
      silenceSoundRef.current?.unloadAsync().catch(() => {});
      silenceSoundRef.current = null;
      alarmSoundRef.current?.stopAsync().catch(() => {});
      alarmSoundRef.current?.unloadAsync().catch(() => {});
      alarmSoundRef.current = null;
    };
  }, []);

  // 修正1: セッションタイマー tick（sessionStarted が true の時のみ動作）
  useEffect(() => {
    if (!sessionStarted) return;
    const id = setInterval(() => {
      if (sessionStartRef.current !== null) {
        setSessionElapsed(Math.floor((Date.now() - sessionStartRef.current) / 1000));
      }
    }, 1000);
    return () => clearInterval(id);
  }, [sessionStarted]);

  // ── インターバルタイマー tick ───────────────────────────────────────────────
  useEffect(() => {
    if (!intervalRunning) return;
    const id = setInterval(async () => {
      const elapsed = (Date.now() - intervalStartRef.current) / 1000;
      const left = Math.ceil(intervalDurationRef.current - elapsed);
      if (left <= 0) {
        setIntervalRunning(false);
        setIntervalRemaining(0);
        notificationIdRef.current = null;
        // 無音ループ停止（バックグラウンドオーディオセッション解放）
        const sil = silenceSoundRef.current;
        silenceSoundRef.current = null;
        if (sil) { try { await sil.stopAsync(); await sil.unloadAsync(); } catch {} }
        // アラーム音再生（playsInSilentModeIOS: true でマナー+イヤホン対応）
        try {
          const { sound } = await Audio.Sound.createAsync(
            // eslint-disable-next-line @typescript-eslint/no-require-imports
            require('../../assets/alarm.wav'),
            { volume: 1.0 },
          );
          alarmSoundRef.current = sound;
          await sound.playAsync();
        } catch {}
        // バイブ（フォアグラウンド時のみ有効）
        Vibration.vibrate([0, 400, 150, 400, 150, 800]);
      } else {
        setIntervalRemaining(left);
      }
    }, 250);
    return () => clearInterval(id);
  }, [intervalRunning]);

  // 修正3 + 修正4: AppState 監視
  useEffect(() => {
    const subscription = AppState.addEventListener('change', async (nextState: AppStateStatus) => {
      const prevState = appStateRef.current;
      appStateRef.current = nextState;

      if ((nextState === 'background' || nextState === 'inactive') && prevState === 'active') {
        // バックグラウンド移行時: インターバル実行中なら通知をスケジュール
        if (intervalRunning) {
          const elapsed = (Date.now() - intervalStartRef.current) / 1000;
          const left = Math.max(1, Math.ceil(intervalDurationRef.current - elapsed));
          const id = await Notifications.scheduleNotificationAsync({
            content: {
              title: 'インターバル終了！',
              body: '次のセットを開始してください',
              sound: true,
            },
            trigger: {
              type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
              seconds: left,
              repeats: false,
              ...(Platform.OS === 'android' && { channelId: 'interval-timer' }),
            },
          });
          notificationIdRef.current = id;
        }
      } else if (nextState === 'active' && prevState !== 'active') {
        // フォアグラウンド復帰時: スケジュール済み通知をキャンセル
        if (notificationIdRef.current) {
          await Notifications.cancelScheduledNotificationAsync(notificationIdRef.current);
          notificationIdRef.current = null;
        }

        // 修正4: UIを強制更新
        if (sessionStartedRef.current && sessionStartRef.current !== null) {
          setSessionElapsed(Math.floor((Date.now() - sessionStartRef.current) / 1000));
        }
        if (intervalRunning) {
          const elapsed = (Date.now() - intervalStartRef.current) / 1000;
          const left = Math.ceil(intervalDurationRef.current - elapsed);
          if (left <= 0) {
            setIntervalRunning(false);
            setIntervalRemaining(0);
          } else {
            setIntervalRemaining(left);
          }
        }
      }
    });
    return () => subscription.remove();
  }, [intervalRunning]);

  // ── インターバル操作 ────────────────────────────────────────────────────────
  async function startInterval() {
    // 初セット完了時にセッションタイマーを自動開始
    if (!sessionStartedRef.current) {
      const now = Date.now();
      sessionStartRef.current = now;
      sessionStartedRef.current = true;
      _savedSessionStartTime = now;
      _savedSessionDate = todayDateStr();
      setSessionStarted(true);
    }
    // 無音ループ開始: バックグラウンド移行後もオーディオセッション+JSを維持する
    try {
      const prev = silenceSoundRef.current;
      if (prev) { await prev.stopAsync(); await prev.unloadAsync(); }
      const { sound: sil } = await Audio.Sound.createAsync(
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        require('../../assets/silence.wav'),
        { isLooping: true, volume: 0 },
      );
      silenceSoundRef.current = sil;
      await sil.playAsync();
    } catch {}
    intervalDurationRef.current = intervalDuration;
    intervalStartRef.current = Date.now();
    setIntervalRemaining(intervalDuration);
    setIntervalRunning(true);
    setShowInterval(true);
  }

  async function resetInterval() {
    // 無音ループ停止
    const sil = silenceSoundRef.current;
    silenceSoundRef.current = null;
    if (sil) { try { await sil.stopAsync(); await sil.unloadAsync(); } catch {} }
    setIntervalRunning(false);
    setIntervalRemaining(null);
  }

  // インターバルタイマー調整（秒単位）
  const adjustInterval = useCallback((delta: number) => {
    if (intervalRunning) {
      const elapsed = (Date.now() - intervalStartRef.current) / 1000;
      const currentRemaining = Math.max(0, intervalDurationRef.current - elapsed);
      const newRemaining = Math.max(10, currentRemaining + delta);
      intervalDurationRef.current = elapsed + newRemaining;
      setIntervalRemaining(Math.ceil(newRemaining));
    } else {
      setIntervalDuration((prev) => Math.max(10, prev + delta));
    }
  }, [intervalRunning]);

  // セッションタイマー調整（秒単位）
  const adjustSession = useCallback((deltaSeconds: number) => {
    if (!sessionStartedRef.current || sessionStartRef.current === null) return;
    const currentElapsed = Math.floor((Date.now() - sessionStartRef.current) / 1000);
    const newElapsed = Math.max(0, currentElapsed + deltaSeconds);
    const newStart = Date.now() - newElapsed * 1000;
    sessionStartRef.current = newStart;
    _savedSessionStartTime = newStart;
    setSessionElapsed(newElapsed);
  }, []);

  // ── セット更新 ──────────────────────────────────────────────────────────────
  function handleDetailUpdated(trainingId: number, updated: TrainingDetail) {
    setTrainings((prev) =>
      prev.map((t) =>
        t.id === trainingId
          ? { ...t, details: t.details.map((d) => (d.id === updated.id ? updated : d)) }
          : t,
      ),
    );
  }

  // ── セット追加 ──────────────────────────────────────────────────────────────
  const handleAddSet = useCallback(async (trainingId: number) => {
    const training = trainings.find((t) => t.id === trainingId);
    if (!training || training.details.length === 0) return;
    const last = training.details[training.details.length - 1];
    try {
      const { data } = await trainingApi.addSet(trainingId, {
        weight: last.weight, reps: last.reps, setType: last.setType,
      });
      setTrainings((prev) => prev.map((t) =>
        t.id === trainingId ? { ...t, details: [...t.details, data] } : t));
    } catch {
      Alert.alert('エラー', 'セット追加に失敗しました');
    }
  }, [trainings]);

  // ── セット削除 ──────────────────────────────────────────────────────────────
  const handleDeleteSet = useCallback((trainingId: number, detailId: number) => {
    Alert.alert('セット削除', 'このセットを削除しますか？', [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '削除', style: 'destructive', onPress: async () => {
          try {
            await trainingApi.deleteSet(detailId);
            setTrainings((prev) => prev.map((t) =>
              t.id === trainingId ? {
                ...t,
                details: t.details.filter((d) => d.id !== detailId)
                  .map((d, i) => ({ ...d, setNumber: i + 1 })),
              } : t));
          } catch (e: any) {
            Alert.alert('エラー', e.response?.data?.error ?? 'セット削除に失敗しました');
          }
        },
      },
    ]);
  }, []);

  // ── トレーニング完了 ────────────────────────────────────────────────────────
  async function handleComplete() {
    const totalSets     = trainings.reduce((s, t) => s + t.details.length, 0);
    const completedSets = trainings.reduce(
      (s, t) => s + t.details.filter((d) => d.completed).length, 0,
    );
    const totalVolume   = trainings.reduce(
      (s, t) => s + t.details.reduce((ds, d) => ds + d.weight * d.reps, 0), 0,
    );
    const isFullyCompleted = completedSets === totalSets && totalSets > 0;

    const alertTitle   = isFullyCompleted ? 'トレーニング完了' : '途中完了の確認';
    const alertMessage = isFullyCompleted
      ? '今日のトレーニングを完了にしますか？'
      : `${completedSets} / ${totalSets} セット完了です。このまま完了にしますか？`;

    Alert.alert(alertTitle, alertMessage, [
      { text: 'キャンセル', style: 'cancel' },
      {
        text: '完了！',
        onPress: async () => {
          // 現時点の経過秒数を確定
          const elapsed = sessionStartRef.current !== null
            ? Math.floor((Date.now() - sessionStartRef.current) / 1000)
            : sessionElapsed;

          if (isFullyCompleted) {
            // 全セット完了：completeTraining を呼んで isAllCompleted=true にし Goal 画面へ
            try {
              for (const t of trainings) {
                await trainingApi.completeTraining(t.id, elapsed);
              }
              setIsCompleting(true);
              _savedSessionStartTime = null;
              _savedSessionDate = null;
              navigation.replace('Goal' as any, {
                date: new Date().toISOString().slice(0, 10),
                totalSets,
                completedSets,
                totalVolume,
                sessionElapsed: elapsed,
              });
            } catch {
              setIsCompleting(false);
              Alert.alert('エラー', '完了処理に失敗しました');
            }
          } else {
            // 途中完了：isAllCompleted を変えない（ホームでボタンを表示し続ける）
            // completeTraining は呼ばない（呼ぶと isAllCompleted=true になってボタンが消える）
            // duration のみ PATCH /training/{id}/duration で保存する
            try {
              for (const t of trainings) {
                await trainingApi.saveDuration(t.id, elapsed);
              }
              setIsCompleting(true);
              // _savedSessionStartTime / _savedSessionDate はリセットしない（タイマー保持）
              navigation.goBack();
            } catch {
              setIsCompleting(false);
              Alert.alert('エラー', '時間の保存に失敗しました');
            }
          }
        },
      },
    ]);
  }

  // ── 表示データ ──────────────────────────────────────────────────────────────
  const sections: TrainingSection[] = trainings.map((t) => ({
    key: String(t.id),
    trainingId: t.id,
    title: t.menu,
    partCode: t.partCode,
    data: t.details,
  }));

  const intervalDisplay  = intervalRemaining !== null ? intervalRemaining : intervalDuration;
  const intervalFinished = intervalRemaining === 0;
  const intervalColor    = intervalFinished ? '#F44336' : intervalRunning ? '#4CAF50' : '#222';

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <SectionList<TrainingDetail, TrainingSection>
        sections={sections}
        keyExtractor={(item) => String(item.id)}
        stickySectionHeadersEnabled={false}
        contentContainerStyle={styles.listContent}

        // ── ヘッダー：セッションタイマー + インターバルタイマー ──────────────
        ListHeaderComponent={
          <View>
            {/* 修正1: セッションタイマー */}
            <View style={styles.sessionBlock}>
              <Text style={styles.sessionLabel}>トレーニング時間</Text>
              <Text style={styles.sessionTime}>
                {sessionStarted ? fmtTime(sessionElapsed) : '--:--'}
              </Text>
            </View>
            {/* タイマー開始ボタン（未開始時） or 時間調整ボタン（開始後） */}
            {!sessionStarted ? (
              <TouchableOpacity
                style={styles.sessionStartBtn}
                onPress={() => {
                  const now = Date.now();
                  sessionStartRef.current = now;
                  sessionStartedRef.current = true;
                  _savedSessionStartTime = now;
                  _savedSessionDate = todayDateStr();
                  setSessionStarted(true);
                }}
              >
                <Text style={styles.sessionStartBtnText}>▶ タイマー開始</Text>
              </TouchableOpacity>
            ) : (
              <View style={styles.sessionAdjRow}>
                <TouchableOpacity style={styles.sessionAdjBtn} onPress={() => adjustSession(-3600)}>
                  <Text style={styles.sessionAdjBtnText}>-60m</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.sessionAdjBtn} onPress={() => adjustSession(-1800)}>
                  <Text style={styles.sessionAdjBtnText}>-30m</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.sessionAdjBtn} onPress={() => adjustSession(1800)}>
                  <Text style={styles.sessionAdjBtnText}>+30m</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.sessionAdjBtn} onPress={() => adjustSession(3600)}>
                  <Text style={styles.sessionAdjBtnText}>+60m</Text>
                </TouchableOpacity>
              </View>
            )}

            {/* インターバルタイマー（折りたたみ） */}
            <View style={styles.intervalCard}>
              <TouchableOpacity
                style={styles.intervalToggleRow}
                onPress={() => setShowInterval((v) => !v)}
                activeOpacity={0.7}
              >
                <Text style={styles.intervalToggleTitle}>インターバルタイマー</Text>
                <View style={styles.intervalToggleRight}>
                  {(intervalRunning || intervalFinished) && (
                    <Text style={[styles.intervalBadge, { color: intervalColor }]}>
                      {fmtTime(intervalDisplay)}
                    </Text>
                  )}
                  <Text style={styles.toggleChevron}>{showInterval ? '▲' : '▼'}</Text>
                </View>
              </TouchableOpacity>

              {showInterval && (
                <View style={styles.intervalBody}>
                  <Text style={[styles.intervalBigTime, { color: intervalColor }]}>
                    {fmtTime(intervalDisplay)}
                  </Text>
                  {intervalFinished && (
                    <Text style={styles.intervalFinishedText}>終了！次のセットへ</Text>
                  )}

                  {/* インターバル調整: ±30s・±60s の4ボタン */}
                  <View style={styles.adjustRow}>
                    <TouchableOpacity style={styles.adjBtn} onPress={() => adjustInterval(-60)}>
                      <Text style={styles.adjBtnText}>-60s</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.adjBtn} onPress={() => adjustInterval(-30)}>
                      <Text style={styles.adjBtnText}>-30s</Text>
                    </TouchableOpacity>
                    <Text style={styles.adjLabel}>{fmtTime(intervalDuration)}</Text>
                    <TouchableOpacity style={styles.adjBtn} onPress={() => adjustInterval(30)}>
                      <Text style={styles.adjBtnText}>+30s</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.adjBtn} onPress={() => adjustInterval(60)}>
                      <Text style={styles.adjBtnText}>+60s</Text>
                    </TouchableOpacity>
                  </View>

                  {/* スタート / リセット */}
                  {!intervalRunning ? (
                    <TouchableOpacity style={styles.intervalStartBtn} onPress={startInterval}>
                      <Text style={styles.intervalStartBtnText}>
                        {intervalRemaining === null ? 'スタート' : 'リスタート'}
                      </Text>
                    </TouchableOpacity>
                  ) : (
                    <TouchableOpacity style={styles.intervalResetBtn} onPress={resetInterval}>
                      <Text style={styles.intervalResetBtnText}>リセット</Text>
                    </TouchableOpacity>
                  )}
                </View>
              )}
            </View>
          </View>
        }

        // ── セクションヘッダー：種目名 ──────────────────────────────────────
        renderSectionHeader={({ section }) => (
          <View style={styles.sectionHeaderWrap}>
            <Text style={styles.partBadge}>
              {PART_LABELS[section.partCode] ?? section.partCode}
            </Text>
            <Text style={styles.menuName}>{section.title}</Text>
            {/* テーブルヘッダー */}
            <View style={styles.tableHeaderRow}>
              <Text style={[styles.colLabel, { width: 52 }]}>セット</Text>
              <Text style={[styles.colLabel, { flex: 1 }]}>重量</Text>
              <Text style={[styles.colLabel, { flex: 1 }]}>回数</Text>
              <Text style={[styles.colLabel, { width: 44 }]}>完了</Text>
              <Text style={[styles.colLabel, { width: 28 }]}> </Text>
            </View>
          </View>
        )}

        // ── セット行 ────────────────────────────────────────────────────────
        renderItem={({ item, section }) => (
          <View style={styles.setRowWrap}>
            <SetRow
              detail={item}
              onUpdated={(updated) => handleDetailUpdated(section.trainingId, updated)}
              onCompleted={startInterval}
              onDelete={() => handleDeleteSet(section.trainingId, item.id)}
              canDelete={section.data.length > 1}
            />
          </View>
        )}

        renderSectionFooter={({ section }) => (
          <View>
            <View style={styles.addSetBtnWrap}>
              <TouchableOpacity
                style={styles.addSetBtn}
                onPress={() => handleAddSet(section.trainingId)}
              >
                <Text style={styles.addSetBtnText}>＋ セット追加</Text>
              </TouchableOpacity>
            </View>
            <View style={styles.sectionGap} />
          </View>
        )}

        // ── 空状態 ──────────────────────────────────────────────────────────
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyText}>種目が登録されていません</Text>
          </View>
        }

        // ── フッター：追加 + 完了 ───────────────────────────────────────────
        ListFooterComponent={
          <View style={styles.footer}>
            <TouchableOpacity
              style={styles.addBtn}
              onPress={() => navigation.navigate('AddExercise')}
            >
              <Text style={styles.addBtnText}>＋ 種目を追加</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.completeBtn} onPress={handleComplete}>
              <Text style={styles.completeBtnText}>🎉 トレーニング完了！</Text>
            </TouchableOpacity>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe:        { flex: 1, backgroundColor: '#f5f5f5' },
  center:      { flex: 1, justifyContent: 'center', alignItems: 'center' },
  listContent: { paddingBottom: 24 },

  // ── セッションタイマー ──────────────────────────────────────────────────────
  sessionBlock: {
    backgroundColor: '#1a1a2e', paddingVertical: 24,
    alignItems: 'center',
  },
  sessionLabel: { fontSize: 11, color: '#888', letterSpacing: 2, marginBottom: 6 },
  sessionTime:  { fontSize: 52, fontWeight: '200', color: '#fff', letterSpacing: 4 },
  sessionStartBtn: {
    backgroundColor: '#1a1a2e', paddingVertical: 12,
    alignItems: 'center', borderTopWidth: 1, borderTopColor: '#2a2a4e',
  },
  sessionStartBtnText: { fontSize: 14, color: '#4CAF50', fontWeight: '700' },
  sessionAdjRow: {
    backgroundColor: '#1a1a2e', flexDirection: 'row', justifyContent: 'center',
    gap: 12, paddingVertical: 10, borderTopWidth: 1, borderTopColor: '#2a2a4e',
  },
  sessionAdjBtn: {
    backgroundColor: '#2a2a4e', paddingHorizontal: 16, paddingVertical: 8, borderRadius: 18,
  },
  sessionAdjBtnText: { fontSize: 14, fontWeight: '700', color: '#4CAF50' },

  // ── インターバルタイマー ────────────────────────────────────────────────────
  intervalCard: {
    backgroundColor: '#fff', marginHorizontal: 16, marginTop: 12, marginBottom: 4,
    borderRadius: 14, overflow: 'hidden',
    shadowColor: '#000', shadowOpacity: 0.05, shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 }, elevation: 2,
  },
  intervalToggleRow: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingHorizontal: 16, paddingVertical: 14,
  },
  intervalToggleTitle: { fontSize: 14, fontWeight: '600', color: '#333' },
  intervalToggleRight: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  intervalBadge:       { fontSize: 16, fontWeight: '700' },
  toggleChevron:       { fontSize: 11, color: '#bbb' },

  intervalBody: {
    borderTopWidth: 1, borderTopColor: '#f0f0f0',
    paddingHorizontal: 20, paddingTop: 16, paddingBottom: 20, alignItems: 'center',
  },
  intervalBigTime:     { fontSize: 64, fontWeight: '200', letterSpacing: 3, marginBottom: 4 },
  intervalFinishedText:{ fontSize: 14, color: '#F44336', fontWeight: '700', marginBottom: 8 },

  adjustRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginVertical: 14, flexWrap: 'wrap', justifyContent: 'center' },
  adjBtn: {
    backgroundColor: '#f0f0f0', paddingHorizontal: 10, paddingVertical: 9, borderRadius: 18,
  },
  adjBtnText: { fontSize: 14, fontWeight: '600', color: '#444' },
  adjLabel:   { fontSize: 17, fontWeight: '600', color: '#555', minWidth: 50, textAlign: 'center' },

  intervalStartBtn: {
    backgroundColor: '#4CAF50', borderRadius: 12, paddingVertical: 14,
    width: '100%', alignItems: 'center',
  },
  intervalStartBtnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  intervalResetBtn: {
    backgroundColor: '#FF9800', borderRadius: 12, paddingVertical: 14,
    width: '100%', alignItems: 'center',
  },
  intervalResetBtnText: { color: '#fff', fontSize: 16, fontWeight: '700' },

  // ── セクション（種目） ──────────────────────────────────────────────────────
  sectionHeaderWrap: {
    backgroundColor: '#fff', marginHorizontal: 16, marginTop: 12,
    paddingHorizontal: 16, paddingTop: 14, paddingBottom: 0,
    borderTopLeftRadius: 12, borderTopRightRadius: 12,
    borderWidth: 1, borderColor: '#eee', borderBottomWidth: 0,
  },
  partBadge: {
    alignSelf: 'flex-start', fontSize: 11, color: '#4CAF50',
    backgroundColor: '#e8f5e9', paddingHorizontal: 8, paddingVertical: 2,
    borderRadius: 8, fontWeight: '600', marginBottom: 4,
  },
  menuName: { fontSize: 18, fontWeight: '800', color: '#222', marginBottom: 10 },
  tableHeaderRow: {
    flexDirection: 'row', paddingBottom: 8,
    borderTopWidth: 1, borderTopColor: '#f0f0f0', paddingTop: 8, gap: 8,
  },
  colLabel: { fontSize: 11, color: '#bbb', fontWeight: '600', textAlign: 'center' },

  setRowWrap: {
    backgroundColor: '#fff', marginHorizontal: 16,
    paddingHorizontal: 16,
    borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#eee',
  },

  // ── セット追加ボタン ────────────────────────────────────────────────────────
  addSetBtnWrap: {
    backgroundColor: '#fff', marginHorizontal: 16,
    paddingHorizontal: 16, paddingTop: 8, paddingBottom: 12,
    borderLeftWidth: 1, borderRightWidth: 1, borderColor: '#eee',
  },
  addSetBtn: {
    borderWidth: 1, borderColor: '#90CAF9', borderRadius: 8,
    paddingVertical: 8, alignItems: 'center',
  },
  addSetBtnText: { color: '#1976D2', fontSize: 13, fontWeight: '600' },

  sectionGap: {
    height: 12, marginHorizontal: 16,
    backgroundColor: '#fff',
    borderBottomLeftRadius: 12, borderBottomRightRadius: 12,
    borderLeftWidth: 1, borderRightWidth: 1, borderBottomWidth: 1, borderColor: '#eee',
  },

  // ── 空状態 ──────────────────────────────────────────────────────────────────
  empty: { paddingVertical: 48, alignItems: 'center' },
  emptyText: { color: '#aaa', fontSize: 15 },

  // ── フッター ────────────────────────────────────────────────────────────────
  footer: { padding: 16, gap: 12 },
  addBtn: {
    backgroundColor: '#fff', borderRadius: 12, padding: 14, alignItems: 'center',
    borderWidth: 1.5, borderColor: '#4CAF50',
  },
  addBtnText:  { color: '#4CAF50', fontSize: 15, fontWeight: '700' },
  completeBtn: {
    backgroundColor: '#FF9800', borderRadius: 12, padding: 16, alignItems: 'center',
  },
  completeBtnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
