import React, { useState, useEffect } from 'react';
import {
  View, Text, FlatList, StyleSheet, ActivityIndicator,
  Alert, TouchableOpacity, TextInput, Modal, Pressable,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Calendar } from 'react-native-calendars';
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
  ARM: '腕', LEG: '脚', CARDIO: 'カーディオ',
};

const PART_CODES = Object.keys(PART_LABELS);

function todayDateString(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export default function ExerciseScreen({ navigation, route }: Props) {
  const { trainingId, date } = route.params;
  const targetDate = date ?? todayDateString();
  const [training, setTraining] = useState<Training | null>(null);
  const [loading, setLoading]   = useState(true);

  // ita7-1 1-1: トレーニング本体（種目名・部位・日付）の編集
  const [editing, setEditing] = useState(false);
  const [editMenu, setEditMenu] = useState('');
  const [editPartCode, setEditPartCode] = useState('');
  const [editDate, setEditDate] = useState(targetDate);
  const [datePickerOpen, setDatePickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await trainingApi.getToday(targetDate);
        const found = data.find((t) => t.id === trainingId) ?? null;
        setTraining(found);
      } catch {
        Alert.alert('エラー', 'データ取得に失敗しました');
      } finally {
        setLoading(false);
      }
    })();
  }, [trainingId, targetDate]);

  function handleDetailUpdated(updated: TrainingDetail) {
    setTraining((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        details: prev.details.map((d) => (d.id === updated.id ? updated : d)),
      };
    });
  }

  function startEditing() {
    if (!training) return;
    setEditMenu(training.menu);
    setEditPartCode(training.partCode);
    setEditDate(training.trainingDate);
    setEditing(true);
  }

  function cancelEditing() {
    setEditing(false);
  }

  async function handleSaveEdit() {
    if (!training || saving) return;
    if (!editMenu.trim()) {
      Alert.alert('入力エラー', '種目名を入力してください');
      return;
    }
    setSaving(true);
    try {
      await trainingApi.updateTraining(training.id, {
        menu: editMenu.trim(),
        partCode: editPartCode,
        trainingDate: editDate,
      });
      setTraining((prev) =>
        prev ? { ...prev, menu: editMenu.trim(), partCode: editPartCode, trainingDate: editDate } : prev,
      );
      navigation.setOptions({ title: editMenu.trim() });
      setEditing(false);
    } catch {
      Alert.alert('エラー', '更新に失敗しました');
    } finally {
      setSaving(false);
    }
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
      {editing ? (
        <View style={styles.editForm}>
          <Text style={styles.fieldLabel}>種目名</Text>
          <TextInput
            style={styles.textInput}
            value={editMenu}
            onChangeText={setEditMenu}
            placeholder="種目名"
          />

          <Text style={styles.fieldLabel}>部位</Text>
          <View style={styles.partRow}>
            {PART_CODES.map((code) => (
              <TouchableOpacity
                key={code}
                style={[styles.partChip, editPartCode === code && styles.partChipSelected]}
                onPress={() => setEditPartCode(code)}
              >
                <Text style={[styles.partChipText, editPartCode === code && styles.partChipTextSelected]}>
                  {PART_LABELS[code]}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <Text style={styles.fieldLabel}>日付</Text>
          <TouchableOpacity style={styles.dateField} onPress={() => setDatePickerOpen(true)}>
            <Text style={styles.dateFieldText}>{editDate}</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.cancelLink} onPress={cancelEditing}>
            <Text style={styles.cancelLinkText}>キャンセル</Text>
          </TouchableOpacity>
        </View>
      ) : (
        <View style={styles.info}>
          <View style={styles.infoTopRow}>
            <Text style={styles.partBadge}>
              {PART_LABELS[training.partCode] ?? training.partCode}
            </Text>
            <TouchableOpacity onPress={startEditing} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Text style={styles.editLink}>編集</Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.menu}>{training.menu}</Text>
          <Text style={styles.progress}>{completed} / {total} セット完了</Text>
          {!!training.memo && <Text style={styles.memoText}>{training.memo}</Text>}
        </View>
      )}

      {/* 日付選択カレンダー（編集フォーム用） */}
      <Modal
        visible={datePickerOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setDatePickerOpen(false)}
      >
        <Pressable style={styles.calendarBackdrop} onPress={() => setDatePickerOpen(false)}>
          <Pressable style={styles.calendarCard} onPress={() => {}}>
            <Calendar
              current={editDate}
              onDayPress={(day: { dateString: string }) => {
                setEditDate(day.dateString);
                setDatePickerOpen(false);
              }}
              markedDates={{ [editDate]: { selected: true, selectedColor: '#4CAF50' } }}
              theme={{
                todayTextColor: '#4CAF50',
                selectedDayBackgroundColor: '#4CAF50',
                arrowColor: '#4CAF50',
              }}
            />
          </Pressable>
        </Pressable>
      </Modal>

      {!editing && (
        <>
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
        </>
      )}

      {editing && <View style={{ flex: 1 }} />}

      {/* フッター：編集中は保存ボタンを固定表示（保存ボタンはフォーム内に置かない） */}
      {editing ? (
        <TouchableOpacity
          style={[styles.saveButton, saving && styles.saveButtonDisabled]}
          onPress={handleSaveEdit}
          disabled={saving}
        >
          {saving ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.saveButtonText}>保存する</Text>
          )}
        </TouchableOpacity>
      ) : (
        <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
          <Text style={styles.backButtonText}>← 一覧に戻る</Text>
        </TouchableOpacity>
      )}
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
  infoTopRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6,
  },
  partBadge: {
    alignSelf: 'flex-start', fontSize: 12, color: '#4CAF50',
    backgroundColor: '#e8f5e9', paddingHorizontal: 10, paddingVertical: 3,
    borderRadius: 10, fontWeight: '600',
  },
  editLink: { fontSize: 13, color: '#4CAF50', fontWeight: '700' },
  menu: { fontSize: 22, fontWeight: '800', color: '#222', marginBottom: 4 },
  progress: { fontSize: 13, color: '#888' },
  memoText: { fontSize: 13, color: '#555', marginTop: 6 },
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

  // 編集フォーム
  editForm: {
    backgroundColor: '#fff', padding: 20,
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  fieldLabel: { fontSize: 13, fontWeight: '700', color: '#333', marginTop: 12, marginBottom: 6 },
  textInput: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10,
    fontSize: 15, color: '#222', backgroundColor: '#fff',
  },
  partRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  partChip: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 16,
    paddingHorizontal: 12, paddingVertical: 6, backgroundColor: '#fff',
  },
  partChipSelected: { borderColor: '#4CAF50', backgroundColor: '#f2faf2' },
  partChipText: { fontSize: 12, color: '#555' },
  partChipTextSelected: { color: '#2e7d32', fontWeight: '700' },
  dateField: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10,
    backgroundColor: '#fff',
  },
  dateFieldText: { fontSize: 15, color: '#222' },
  cancelLink: { marginTop: 18, alignSelf: 'center', padding: 6 },
  cancelLinkText: { fontSize: 13, color: '#999', fontWeight: '600' },

  calendarBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'center', alignItems: 'center',
  },
  calendarCard: {
    width: '90%', backgroundColor: '#fff', borderRadius: 16, padding: 12,
    shadowColor: '#000', shadowOpacity: 0.2, shadowRadius: 16, shadowOffset: { width: 0, height: 8 },
    elevation: 8,
  },

  saveButton: {
    margin: 16, padding: 16, backgroundColor: '#4CAF50',
    borderRadius: 12, alignItems: 'center',
  },
  saveButtonDisabled: { opacity: 0.6 },
  saveButtonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
