import React, { useEffect, useState } from 'react';
import { View, ActivityIndicator, StyleSheet, Platform } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { Audio } from 'expo-av';
import * as SplashScreen from 'expo-splash-screen';
import { getTokens } from './src/auth/tokenStore';
import RootNavigator from './src/navigation/AppNavigator';

// ネイティブスプラッシュスクリーンを、初期化処理が完了する（hideAsync()を呼ぶ）まで表示し続ける。
// コンポーネント宣言より前（モジュールスコープ）で呼び出す必要がある。
SplashScreen.preventAutoHideAsync().catch(() => {
  // 既に非表示になっている等で失敗しても致命的ではないため握りつぶす
});

// フォアグラウンド時の通知表示設定（SDK 54: shouldShowAlert → shouldShowBanner/shouldShowList）
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export default function App() {
  const [initialRoute, setInitialRoute] = useState<'Auth' | 'App' | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const tokens = await getTokens();
        setInitialRoute(tokens.accessToken ? 'App' : 'Auth');

        // Android: バイブレーションパターン付き通知チャンネルを作成
        if (Platform.OS === 'android') {
          await Notifications.setNotificationChannelAsync('interval-timer', {
            name: 'インターバルタイマー',
            importance: Notifications.AndroidImportance.HIGH,
            vibrationPattern: [0, 400, 150, 400, 150, 800],
            sound: 'default',
          });
        }

        // マナーモード + イヤホン時でも音が出るよう AVAudioSession を playback に設定
        await Audio.setAudioModeAsync({
          playsInSilentModeIOS: true,
          staysActiveInBackground: true,
        });
      } finally {
        // 初期化処理が失敗した場合でもスプラッシュを表示し続けたままにしない
        await SplashScreen.hideAsync().catch(() => {});
      }
    })();
  }, []);

  if (!initialRoute) {
    return (
      <View style={styles.splash}>
        <ActivityIndicator size="large" color="#4CAF50" />
      </View>
    );
  }

  return (
    <GestureHandlerRootView style={styles.root}>
      <SafeAreaProvider>
        <RootNavigator initialRoute={initialRoute} />
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  splash: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#fff' },
});
