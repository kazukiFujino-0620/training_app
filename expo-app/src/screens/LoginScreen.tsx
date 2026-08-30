import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  Alert, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { AuthStackParamList } from '../navigation/AppNavigator';
import { authApi } from '../api/client';
import { saveTokens, getOrCreateDeviceId } from '../auth/tokenStore';
import { SERVER_ORIGIN } from '../config';

type Props = {
  navigation: NativeStackNavigationProp<AuthStackParamList, 'Login'>;
};

const OAUTH_REDIRECT_URL = Linking.createURL('oauth-callback');

const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  cancelled: 'ログインがキャンセルされました',
  invalid_state: 'ログインの検証に失敗しました。もう一度お試しください',
  not_registered:
    'このアカウントはまだ登録されていません。先にWebサイトでGoogle/LINEログインを行ってください',
  provider_error: '認証サーバーとの通信に失敗しました。時間をおいて再度お試しください',
};

export default function LoginScreen({ navigation }: Props) {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading]   = useState(false);
  const [oauthLoading, setOauthLoading] = useState<'google' | 'line' | null>(null);

  async function handleOAuthLogin(provider: 'google' | 'line') {
    setOauthLoading(provider);
    try {
      const deviceId = await getOrCreateDeviceId();
      const startUrl =
        `${SERVER_ORIGIN}/mobile-oauth/${provider}/start?deviceId=${encodeURIComponent(deviceId)}`;
      const result = await WebBrowser.openAuthSessionAsync(startUrl, OAUTH_REDIRECT_URL);

      if (result.type !== 'success' || !result.url) {
        return;
      }

      const { queryParams } = Linking.parse(result.url);
      const error = queryParams?.error as string | undefined;
      if (error) {
        Alert.alert('ログイン失敗', OAUTH_ERROR_MESSAGES[error] ?? 'ログインに失敗しました');
        return;
      }

      const mfaRequired = queryParams?.mfaRequired as string | undefined;
      const mfaTempToken = queryParams?.mfaTempToken as string | undefined;
      if (mfaRequired === 'true' && mfaTempToken) {
        navigation.navigate('Mfa', { mfaTempToken, deviceId });
        return;
      }

      const accessToken = queryParams?.accessToken as string | undefined;
      const refreshToken = queryParams?.refreshToken as string | undefined;
      if (accessToken && refreshToken) {
        await saveTokens(accessToken, refreshToken, deviceId);
        navigation.replace('App' as any);
        return;
      }

      Alert.alert('ログイン失敗', 'ログインに失敗しました');
    } catch (e: any) {
      Alert.alert('ログイン失敗', e.message ?? 'ログインに失敗しました');
    } finally {
      setOauthLoading(null);
    }
  }

  async function handleLogin() {
    if (!email.trim() || !password) {
      Alert.alert('入力エラー', 'メールアドレスとパスワードを入力してください');
      return;
    }
    setLoading(true);
    try {
      const deviceId = await getOrCreateDeviceId();
      const { data } = await authApi.login({ email: email.trim(), password, deviceId });

      if (data.mfaRequired && data.mfaTempToken) {
        navigation.navigate('Mfa', { mfaTempToken: data.mfaTempToken, deviceId });
      } else if (data.accessToken && data.refreshToken) {
        await saveTokens(data.accessToken, data.refreshToken, deviceId);
        navigation.replace('App' as any);
      }
    } catch (e: any) {
      if (e.response?.data?.errorCode === 'OAUTH_ONLY_ACCOUNT') {
        Alert.alert(
          'Google/LINEアカウント',
          'このアカウントはGoogle/LINEでログインしています。\n\nWebサイトの「パスワードをお忘れですか？」からパスワードを設定すると、モバイルアプリにもログインできます。',
        );
      } else {
        const msg = e.response?.data?.error ?? e.message ?? 'ログインに失敗しました';
        Alert.alert('ログイン失敗', String(msg));
      }
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
        <Feather name="activity" size={56} color="#4CAF50" style={styles.logo} />
        <Text style={styles.title}>トレーニングアプリ</Text>
        <Text style={styles.subtitle}>アカウントにログイン</Text>

        <TextInput
          style={styles.input}
          placeholder="メールアドレス"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          autoComplete="email"
          returnKeyType="next"
        />
        <TextInput
          style={styles.input}
          placeholder="パスワード"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoComplete="password"
          returnKeyType="done"
          onSubmitEditing={handleLogin}
        />

        <TouchableOpacity
          style={[styles.button, loading && styles.buttonDisabled]}
          onPress={handleLogin}
          disabled={loading}
        >
          {loading
            ? <ActivityIndicator color="#fff" />
            : <Text style={styles.buttonText}>ログイン</Text>}
        </TouchableOpacity>

        <View style={styles.divider}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerText}>または</Text>
          <View style={styles.dividerLine} />
        </View>

        <TouchableOpacity
          style={[styles.oauthButton, styles.googleButton, oauthLoading && styles.buttonDisabled]}
          onPress={() => handleOAuthLogin('google')}
          disabled={oauthLoading !== null}
        >
          {oauthLoading === 'google'
            ? <ActivityIndicator color="#444" />
            : <Text style={styles.googleButtonText}>Googleでログイン</Text>}
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.oauthButton, styles.lineButton, oauthLoading && styles.buttonDisabled]}
          onPress={() => handleOAuthLogin('line')}
          disabled={oauthLoading !== null}
        >
          {oauthLoading === 'line'
            ? <ActivityIndicator color="#fff" />
            : <Text style={styles.lineButtonText}>LINEでログイン</Text>}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#f5f5f5' },
  container: {
    flex: 1, justifyContent: 'center', paddingHorizontal: 32,
  },
  logo: { alignSelf: 'center', marginBottom: 8 },
  title: {
    fontSize: 26, fontWeight: '800', color: '#222',
    textAlign: 'center', marginBottom: 4,
  },
  subtitle: {
    fontSize: 14, color: '#888', textAlign: 'center', marginBottom: 36,
  },
  input: {
    backgroundColor: '#fff', borderRadius: 10, padding: 14,
    fontSize: 16, marginBottom: 12, borderWidth: 1, borderColor: '#e0e0e0',
  },
  button: {
    backgroundColor: '#4CAF50', borderRadius: 10, padding: 16,
    alignItems: 'center', marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  divider: {
    flexDirection: 'row', alignItems: 'center', marginTop: 24, marginBottom: 16,
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: '#e0e0e0' },
  dividerText: { marginHorizontal: 12, color: '#999', fontSize: 12 },
  oauthButton: {
    borderRadius: 10, padding: 16, alignItems: 'center', marginBottom: 12,
  },
  googleButton: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#dadce0' },
  googleButtonText: { color: '#3c4043', fontSize: 15, fontWeight: '700' },
  lineButton: { backgroundColor: '#06C755' },
  lineButtonText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
