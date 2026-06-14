import React, { useEffect, useState } from 'react';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { getTokens } from './src/auth/tokenStore';
import RootNavigator from './src/navigation/AppNavigator';

// フォアグラウンド時の通知表示設定（これがないと通知が無視される）
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
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
