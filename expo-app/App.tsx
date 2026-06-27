import React, { useEffect, useState } from 'react';
import { View, ActivityIndicator, StyleSheet, Platform } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { Audio } from 'expo-av';
import { getTokens } from './src/auth/tokenStore';
import RootNavigator from './src/navigation/AppNavigator';

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
