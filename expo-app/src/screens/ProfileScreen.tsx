import React, { useCallback, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, ScrollView, Switch,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { profileApi } from '../api/client';
import type { GoalMode } from '../api/types';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Profile'>;
};

const GOAL_MODE_OPTIONS: { value: GoalMode; label: string }[] = [
  { value: 'BULKING', label: '筋肥大' },
  { value: 'CUTTING', label: '減量' },
  { value: 'MAINTENANCE', label: '維持' },
];

const GENDER_OPTIONS = [
  { value: 'MALE', label: '男性' },
  { value: 'FEMALE', label: '女性' },
  { value: 'OTHER', label: 'その他' },
];

/** プロフィール編集画面（ita7-1 1-3）。基本項目・目標モード・AI同意設定の3セクション構成。 */
export default function ProfileScreen({ navigation }: Props) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const [userName, setUserName] = useState('');
  const [heightCm, setHeightCm] = useState('');
  const [weightKg, setWeightKg] = useState('');
  const [gender, setGender] = useState<string | null>(null);
  const [birthDate, setBirthDate] = useState('');
  const [goalMode, setGoalMode] = useState<GoalMode>('MAINTENANCE');
  const [aiAdviceConsent, setAiAdviceConsent] = useState(false);
  const [togglingConsent, setTogglingConsent] = useState(false);
  const [savingGoalMode, setSavingGoalMode] = useState(false);

  const load = useCallback(async () => {
    try {
      const { data } = await profileApi.get();
      setUserName(data.userName ?? '');
      setHeightCm(data.heightCm != null ? String(data.heightCm) : '');
      setWeightKg(data.weightKg != null ? String(data.weightKg) : '');
      setGender(data.gender);
      setBirthDate(data.birthDate ?? '');
      setGoalMode((data.currentGoalMode as GoalMode) ?? 'MAINTENANCE');
      setAiAdviceConsent(!!data.aiAdviceConsent);
    } catch {
      Alert.alert('エラー', 'プロフィールの取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  async function handleSaveBasicInfo() {
    if (heightCm && (Number(heightCm) < 100 || Number(heightCm) > 250)) {
      Alert.alert('入力エラー', '身長は100〜250cmの範囲で入力してください');
      return;
    }
    if (weightKg && (Number(weightKg) < 20 || Number(weightKg) > 300)) {
      Alert.alert('入力エラー', '体重は20〜300kgの範囲で入力してください');
      return;
    }
    setSaving(true);
    try {
      await profileApi.update({
        userName: userName.trim() || undefined,
        heightCm: heightCm ? Number(heightCm) : undefined,
        weightKg: weightKg ? Number(weightKg) : undefined,
        gender: gender ?? undefined,
        birthDate: birthDate || undefined,
      });
      Alert.alert('保存しました', 'プロフィールを更新しました');
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.error ?? '保存に失敗しました');
    } finally {
      setSaving(false);
    }
  }

  async function handleSelectGoalMode(mode: GoalMode) {
    if (mode === goalMode || savingGoalMode) return;
    const prev = goalMode;
    setGoalMode(mode);
    setSavingGoalMode(true);
    try {
      await profileApi.updateGoalMode(mode);
    } catch {
      setGoalMode(prev);
      Alert.alert('エラー', '目標モードの変更に失敗しました');
    } finally {
      setSavingGoalMode(false);
    }
  }

  async function handleToggleConsent(value: boolean) {
    const prev = aiAdviceConsent;
    setAiAdviceConsent(value);
    setTogglingConsent(true);
    try {
      await profileApi.updateAiAdviceConsent(value);
    } catch {
      setAiAdviceConsent(prev);
      Alert.alert('エラー', 'AI機能設定の変更に失敗しました');
    } finally {
      setTogglingConsent(false);
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

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Feather name="chevron-left" size={26} color="#222" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>プロフィール</Text>
        <View style={{ width: 32 }} />
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {/* 基本項目 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>基本項目</Text>

          <Text style={styles.fieldLabel}>ユーザー名</Text>
          <TextInput
            style={styles.input}
            value={userName}
            onChangeText={setUserName}
            placeholder="ユーザー名"
            maxLength={50}
          />

          <Text style={styles.fieldLabel}>身長（cm）</Text>
          <TextInput
            style={styles.input}
            value={heightCm}
            onChangeText={setHeightCm}
            placeholder="例: 170"
            keyboardType="decimal-pad"
          />

          <Text style={styles.fieldLabel}>体重（kg）</Text>
          <TextInput
            style={styles.input}
            value={weightKg}
            onChangeText={setWeightKg}
            placeholder="例: 65"
            keyboardType="decimal-pad"
          />

          <Text style={styles.fieldLabel}>性別</Text>
          <View style={styles.chipRow}>
            {GENDER_OPTIONS.map((opt) => (
              <TouchableOpacity
                key={opt.value}
                style={[styles.chip, gender === opt.value && styles.chipSelected]}
                onPress={() => setGender(gender === opt.value ? null : opt.value)}
              >
                <Text style={[styles.chipText, gender === opt.value && styles.chipTextSelected]}>
                  {opt.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>

          <Text style={styles.fieldLabel}>生年月日</Text>
          <TextInput
            style={styles.input}
            value={birthDate}
            onChangeText={setBirthDate}
            placeholder="YYYY-MM-DD"
            autoCapitalize="none"
          />

          <TouchableOpacity
            style={[styles.saveButton, saving && styles.saveButtonDisabled]}
            onPress={handleSaveBasicInfo}
            disabled={saving}
          >
            {saving ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.saveButtonText}>基本項目を保存</Text>
            )}
          </TouchableOpacity>
        </View>

        {/* 目標モード */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>目標モード</Text>
          <View style={styles.segmentRow}>
            {GOAL_MODE_OPTIONS.map((opt) => (
              <TouchableOpacity
                key={opt.value}
                style={[styles.segmentItem, goalMode === opt.value && styles.segmentItemSelected]}
                onPress={() => handleSelectGoalMode(opt.value)}
                disabled={savingGoalMode}
              >
                <Text
                  style={[
                    styles.segmentItemText,
                    goalMode === opt.value && styles.segmentItemTextSelected,
                  ]}
                >
                  {opt.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* AI同意設定 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>AI機能</Text>
          <View style={styles.switchRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.switchLabel}>AIトレーニング提案・疲労度分析を利用する</Text>
              <Text style={styles.switchSubLabel}>
                トレーニング提案・疲労度分析・トレーナーアドバイス下書きの利用に同意します
              </Text>
            </View>
            <Switch
              value={aiAdviceConsent}
              onValueChange={handleToggleConsent}
              disabled={togglingConsent}
              trackColor={{ true: '#4CAF50' }}
            />
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 8, paddingVertical: 12, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backButton: { padding: 6 },
  headerTitle: { fontSize: 17, fontWeight: '800', color: '#222' },
  content: { padding: 16, paddingBottom: 40 },
  section: {
    backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 16,
  },
  sectionTitle: { fontSize: 15, fontWeight: '800', color: '#222', marginBottom: 8 },
  fieldLabel: { fontSize: 13, fontWeight: '700', color: '#333', marginTop: 12, marginBottom: 6 },
  input: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10,
    fontSize: 15, color: '#222', backgroundColor: '#fff',
  },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 16,
    paddingHorizontal: 14, paddingVertical: 7, backgroundColor: '#fff',
  },
  chipSelected: { borderColor: '#4CAF50', backgroundColor: '#f2faf2' },
  chipText: { fontSize: 13, color: '#555' },
  chipTextSelected: { color: '#2e7d32', fontWeight: '700' },
  saveButton: {
    marginTop: 18, backgroundColor: '#4CAF50', borderRadius: 10,
    paddingVertical: 14, alignItems: 'center',
  },
  saveButtonDisabled: { opacity: 0.6 },
  saveButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
  segmentRow: {
    flexDirection: 'row', backgroundColor: '#f2f2f2', borderRadius: 10, padding: 4, gap: 4,
  },
  segmentItem: {
    flex: 1, paddingVertical: 10, borderRadius: 8, alignItems: 'center',
  },
  segmentItemSelected: { backgroundColor: '#4CAF50' },
  segmentItemText: { fontSize: 13, fontWeight: '700', color: '#666' },
  segmentItemTextSelected: { color: '#fff' },
  switchRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  switchLabel: { fontSize: 14, fontWeight: '700', color: '#222' },
  switchSubLabel: { fontSize: 12, color: '#888', marginTop: 4, lineHeight: 17 },
});
