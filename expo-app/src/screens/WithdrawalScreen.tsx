import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, ScrollView,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useFocusEffect } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';
import { withdrawalApi } from '../api/client';
import { clearTokens } from '../auth/tokenStore';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Withdrawal'>;
};

const REASON_OPTIONS = [
  { value: 'NO_LONGER_USED', label: '使わなくなった' },
  { value: 'SWITCHING_SERVICE', label: '別のサービスに移行する' },
  { value: 'OTHER', label: 'その他' },
];

/** ita3-3: 退会画面。ジム所属ユーザーは申請制（管理者承認後に削除）、一般ユーザーは即時削除。 */
export default function WithdrawalScreen({ navigation }: Props) {
  const [loading, setLoading] = useState(true);
  const [isGeneralUser, setIsGeneralUser] = useState(false);
  const [hasPendingRequest, setHasPendingRequest] = useState(false);
  const [reasonType, setReasonType] = useState<string | null>(null);
  const [reasonText, setReasonText] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const loadStatus = useCallback(async () => {
    setLoading(true);
    try {
      const { data } = await withdrawalApi.getStatus();
      setIsGeneralUser(data.isGeneralUser);
      setHasPendingRequest(data.hasPendingRequest);
    } catch {
      Alert.alert('エラー', '状態の取得に失敗しました');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => { loadStatus(); }, [loadStatus]));

  async function handleCancelRequest() {
    try {
      await withdrawalApi.cancel();
      await loadStatus();
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.error ?? 'キャンセルに失敗しました');
    }
  }

  async function handleSubmit() {
    if (!agreed || submitting) return;
    setSubmitting(true);
    try {
      if (isGeneralUser) {
        await withdrawalApi.deleteImmediately();
        await clearTokens();
        navigation.replace('Auth' as any);
      } else {
        await withdrawalApi.request(reasonType ?? undefined, reasonText || undefined);
        await loadStatus();
      }
    } catch (e: any) {
      Alert.alert('エラー', e.response?.data?.error ?? '処理に失敗しました');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </View>
    );
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backButton}>
          <Feather name="chevron-left" size={26} color="#222" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{isGeneralUser ? 'アカウント削除' : '退会申請'}</Text>
      </View>

      {hasPendingRequest ? (
        <View style={styles.body}>
          <View style={styles.pendingBox}>
            <Text style={styles.pendingBadge}>申請中</Text>
            <Text style={styles.pendingText}>
              退会申請を受け付けています。{'\n'}管理者が処理するまでお待ちください。
            </Text>
            <Text style={styles.pendingSubText}>申請をキャンセルする場合は下のボタンを押してください。</Text>
            <TouchableOpacity style={styles.cancelButton} onPress={handleCancelRequest}>
              <Text style={styles.cancelButtonText}>申請をキャンセルする</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <ScrollView style={styles.body} keyboardShouldPersistTaps="handled">
          <View style={styles.warnBox}>
            <Text style={styles.warnTitle}>注意事項</Text>
            {isGeneralUser ? (
              <>
                <Text style={styles.warnItem}>・削除するとアカウントおよび全てのトレーニングデータが直ちに削除されます</Text>
                <Text style={styles.warnItem}>・この操作は取り消せません。データの復元はできません</Text>
                <Text style={styles.warnItem}>・削除後は自動的にログアウトされます</Text>
              </>
            ) : (
              <>
                <Text style={styles.warnItem}>・退会するとアカウントおよび全てのトレーニングデータが削除されます</Text>
                <Text style={styles.warnItem}>・管理者による承認後、データの復元はできません</Text>
                <Text style={styles.warnItem}>・退会完了まで通常通りご利用いただけます</Text>
              </>
            )}
          </View>

          {!isGeneralUser && (
            <>
              <Text style={styles.fieldLabel}>退会理由（任意）</Text>
              <View style={styles.reasonRow}>
                {REASON_OPTIONS.map((opt) => (
                  <TouchableOpacity
                    key={opt.value}
                    style={[styles.reasonChip, reasonType === opt.value && styles.reasonChipSelected]}
                    onPress={() => setReasonType(reasonType === opt.value ? null : opt.value)}
                  >
                    <Text style={[styles.reasonChipText, reasonType === opt.value && styles.reasonChipTextSelected]}>
                      {opt.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={styles.fieldLabel}>詳細（任意・500文字以内）</Text>
              <TextInput
                style={styles.textarea}
                multiline
                numberOfLines={3}
                maxLength={500}
                placeholder="ご意見・ご要望があればお聞かせください"
                value={reasonText}
                onChangeText={setReasonText}
              />
            </>
          )}

          <TouchableOpacity
            testID="withdrawal-agree-checkbox"
            style={styles.checkboxRow}
            onPress={() => setAgreed(!agreed)}
          >
            <View style={[styles.checkbox, agreed && styles.checkboxChecked]}>
              {agreed && <Feather name="check" size={14} color="#fff" />}
            </View>
            <Text style={styles.checkboxLabel}>
              {isGeneralUser
                ? '全てのデータが直ちに削除されることを理解し、アカウントを削除します。この操作は取り消せません。'
                : '全てのデータが削除されることを理解し、退会申請します。この操作は取り消せません。'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            testID="withdrawal-submit-button"
            style={[styles.submitButton, (!agreed || submitting) && styles.submitButtonDisabled]}
            onPress={handleSubmit}
            disabled={!agreed || submitting}
          >
            {submitting ? (
              <ActivityIndicator size="small" color="#fff" />
            ) : (
              <Text style={styles.submitButtonText}>
                {isGeneralUser ? 'アカウントを削除する' : '退会を申請する'}
              </Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#f5f5f5' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  header: {
    flexDirection: 'row', alignItems: 'center', gap: 4,
    paddingHorizontal: 8, paddingVertical: 12, backgroundColor: '#fff',
    borderBottomWidth: 1, borderBottomColor: '#eee',
  },
  backButton: { padding: 6 },
  headerTitle: { fontSize: 17, fontWeight: '800', color: '#222' },
  body: { flex: 1, padding: 16 },

  warnBox: {
    backgroundColor: '#fff5f5', borderWidth: 1, borderColor: '#e53e3e',
    borderRadius: 10, padding: 14, marginBottom: 16,
  },
  warnTitle: { color: '#c62828', fontWeight: '700', fontSize: 13, marginBottom: 6 },
  warnItem: { color: '#c62828', fontSize: 12, lineHeight: 18 },

  fieldLabel: { fontSize: 13, fontWeight: '700', color: '#333', marginTop: 14, marginBottom: 6 },
  reasonRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  reasonChip: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 16,
    paddingHorizontal: 12, paddingVertical: 6, backgroundColor: '#fff',
  },
  reasonChipSelected: { borderColor: '#4CAF50', backgroundColor: '#f2faf2' },
  reasonChipText: { fontSize: 12, color: '#555' },
  reasonChipTextSelected: { color: '#2e7d32', fontWeight: '700' },
  textarea: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10,
    fontSize: 13, color: '#333', backgroundColor: '#fff', minHeight: 72, textAlignVertical: 'top',
  },

  checkboxRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8, marginTop: 18 },
  checkbox: {
    width: 20, height: 20, borderRadius: 4, borderWidth: 1.5, borderColor: '#ccc',
    alignItems: 'center', justifyContent: 'center', marginTop: 1,
  },
  checkboxChecked: { backgroundColor: '#e53935', borderColor: '#e53935' },
  checkboxLabel: { flex: 1, fontSize: 12.5, color: '#333', lineHeight: 18 },

  submitButton: {
    marginTop: 18, backgroundColor: '#e53935', borderRadius: 10, padding: 14, alignItems: 'center',
  },
  submitButtonDisabled: { opacity: 0.4 },
  submitButtonText: { color: '#fff', fontSize: 14, fontWeight: '700' },

  pendingBox: {
    backgroundColor: '#fff', borderWidth: 1, borderColor: '#eee', borderRadius: 12,
    padding: 28, alignItems: 'center', marginTop: 20,
  },
  pendingBadge: {
    backgroundColor: '#fff8e1', color: '#b8860b', fontSize: 11, fontWeight: '700',
    paddingHorizontal: 12, paddingVertical: 4, borderRadius: 999, overflow: 'hidden', marginBottom: 12,
  },
  pendingText: { fontSize: 13, color: '#555', textAlign: 'center', lineHeight: 20, marginBottom: 8 },
  pendingSubText: { fontSize: 11, color: '#888', textAlign: 'center', marginBottom: 16 },
  cancelButton: {
    borderWidth: 1, borderColor: '#ddd', borderRadius: 10, paddingVertical: 12,
    paddingHorizontal: 20, backgroundColor: '#fff',
  },
  cancelButtonText: { fontSize: 13, fontWeight: '700', color: '#666' },
});
