import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { AuthStackParamList } from '../navigation/AppNavigator';
import { authApi } from '../api/client';
import { saveTokens } from '../auth/tokenStore';

type Props = {
  navigation: NativeStackNavigationProp<AuthStackParamList, 'Mfa'>;
  route: RouteProp<AuthStackParamList, 'Mfa'>;
};

export default function MfaScreen({ navigation, route }: Props) {
  const { mfaTempToken, deviceId } = route.params;
  const [otp, setOtp]               = useState('');
  const [backupCode, setBackupCode] = useState('');
  const [useBackup, setUseBackup]   = useState(false);
  const [loading, setLoading]       = useState(false);

  async function handleVerify() {
    const code = useBackup ? backupCode.trim() : otp.trim();
    if (!code) {
      Alert.alert('入力エラー', useBackup ? 'バックアップコードを入力してください' : '6桁のコードを入力してください');
      return;
    }
    setLoading(true);
    try {
      const { data } = await authApi.mfaVerify({
        mfaTempToken,
        ...(useBackup ? { backupCode: code } : { otp: code }),
      });
      if (data.accessToken && data.refreshToken) {
        await saveTokens(data.accessToken, data.refreshToken, deviceId);
        navigation.replace('App' as any);
      }
    } catch (e: any) {
      const msg = e.response?.data?.message ?? e.response?.data ?? '認証に失敗しました';
      Alert.alert('認証失敗', String(msg));
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <View style={styles.container}>
        <Text style={styles.icon}>🔐</Text>
        <Text style={styles.title}>2段階認証</Text>
        <Text style={styles.subtitle}>
          {useBackup
            ? 'バックアップコードを入力してください'
            : '認証アプリの6桁のコードを入力してください'}
        </Text>

        {useBackup ? (
          <TextInput
            style={styles.input}
            placeholder="バックアップコード"
            value={backupCode}
            onChangeText={setBackupCode}
            autoCapitalize="characters"
            autoComplete="one-time-code"
          />
        ) : (
          <TextInput
            style={[styles.input, styles.otpInput]}
            placeholder="000000"
            value={otp}
            onChangeText={(t) => setOtp(t.replace(/\D/g, '').slice(0, 6))}
            keyboardType="number-pad"
            maxLength={6}
            autoComplete="one-time-code"
          />
        )}

        <TouchableOpacity
          style={[styles.button, loading && styles.buttonDisabled]}
          onPress={handleVerify}
          disabled={loading}
        >
          {loading
            ? <ActivityIndicator color="#fff" />
            : <Text style={styles.buttonText}>確認</Text>}
        </TouchableOpacity>

        <TouchableOpacity onPress={() => setUseBackup(!useBackup)} style={styles.toggle}>
          <Text style={styles.toggleText}>
            {useBackup ? '→ 認証アプリのコードを使う' : '→ バックアップコードを使う'}
          </Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#f5f5f5' },
  container: { flex: 1, justifyContent: 'center', paddingHorizontal: 32 },
  icon: { fontSize: 48, textAlign: 'center', marginBottom: 8 },
  title: { fontSize: 24, fontWeight: '800', color: '#222', textAlign: 'center', marginBottom: 8 },
  subtitle: { fontSize: 14, color: '#666', textAlign: 'center', marginBottom: 32, lineHeight: 20 },
  input: {
    backgroundColor: '#fff', borderRadius: 10, padding: 14,
    fontSize: 16, marginBottom: 12, borderWidth: 1, borderColor: '#e0e0e0',
  },
  otpInput: { fontSize: 28, letterSpacing: 8, textAlign: 'center', fontWeight: '700' },
  button: {
    backgroundColor: '#4CAF50', borderRadius: 10, padding: 16,
    alignItems: 'center', marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  toggle: { marginTop: 20, alignItems: 'center' },
  toggleText: { color: '#4CAF50', fontSize: 14 },
});
