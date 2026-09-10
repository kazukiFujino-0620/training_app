import React, { Suspense } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import LoginScreen from '../screens/LoginScreen';
import MfaScreen from '../screens/MfaScreen';
import type { AiTrainingSuggestion } from '../api/types';

// AppStack配下の画面は、未ログイン時にも起動のたびにJS評価コストが発生していた
// （AppNavigator.tsxの静的importにより、ログイン前後を問わずモジュール本体が評価される）。
// itバグ-25対応: React.lazyで遅延importし、実際に画面へ遷移するまで評価を遅らせる。
// LoginScreen/MfaScreenは起動直後（未ログイン時）に必ず表示されるため対象外。
const TrainingListScreen = React.lazy(() => import('../screens/TrainingListScreen'));
const ExerciseScreen = React.lazy(() => import('../screens/ExerciseScreen'));
const AddExerciseScreen = React.lazy(() => import('../screens/AddExerciseScreen'));
const TrainingStartScreen = React.lazy(() => import('../screens/TrainingStartScreen'));
const GoalScreen = React.lazy(() => import('../screens/GoalScreen'));
const HealthScreen = React.lazy(() => import('../screens/HealthScreen'));
const NoticeListScreen = React.lazy(() => import('../screens/NoticeListScreen'));
const WithdrawalScreen = React.lazy(() => import('../screens/WithdrawalScreen'));
const BodyMeasurementScreen = React.lazy(() => import('../screens/BodyMeasurementScreen'));
const ProfileScreen = React.lazy(() => import('../screens/ProfileScreen'));

// 画面モジュールの読み込み中に表示するフォールバック。
// App.tsx起動時スプラッシュの配色（白背景 + 緑のActivityIndicator）に合わせている。
function ScreenLoadingFallback() {
  return (
    <View style={styles.loadingContainer}>
      <ActivityIndicator size="large" color="#4CAF50" />
    </View>
  );
}

// React Navigationの`component`にはSuspense境界を持つ安定したコンポーネント参照を渡す必要があるため、
// レンダー関数の外（モジュールスコープ）で一度だけラップする（レンダーの度に新しい関数を生成しない）。
function withSuspense<P extends object>(LazyComponent: React.ComponentType<P>) {
  return function SuspendedScreen(props: P) {
    return (
      <Suspense fallback={<ScreenLoadingFallback />}>
        <LazyComponent {...props} />
      </Suspense>
    );
  };
}

const TrainingListScreenLazy = withSuspense(TrainingListScreen);
const ExerciseScreenLazy = withSuspense(ExerciseScreen);
const AddExerciseScreenLazy = withSuspense(AddExerciseScreen);
const TrainingStartScreenLazy = withSuspense(TrainingStartScreen);
const GoalScreenLazy = withSuspense(GoalScreen);
const HealthScreenLazy = withSuspense(HealthScreen);
const NoticeListScreenLazy = withSuspense(NoticeListScreen);
const WithdrawalScreenLazy = withSuspense(WithdrawalScreen);
const BodyMeasurementScreenLazy = withSuspense(BodyMeasurementScreen);
const ProfileScreenLazy = withSuspense(ProfileScreen);

export type AuthStackParamList = {
  Login: undefined;
  Mfa: { mfaTempToken: string; deviceId: string };
};

export type AppStackParamList = {
  TrainingList: undefined;
  TrainingStart: undefined;
  /** date未指定時は当日として扱う（後方互換） */
  Exercise: { trainingId: number; menu: string; date?: string };
  AddExercise: { aiSuggestion?: AiTrainingSuggestion; date?: string } | undefined;
  Goal: {
    date: string;
    totalSets?: number;
    completedSets?: number;
    totalVolume?: number;
    sessionElapsed?: number;
  };
  Health: undefined;
  NoticeList: undefined;
  Withdrawal: undefined;
  BodyMeasurement: undefined;
  Profile: undefined;
};

type RootStackParamList = {
  Auth: undefined;
  App: undefined;
};

const Root = createNativeStackNavigator<RootStackParamList>();
const AuthStack = createNativeStackNavigator<AuthStackParamList>();
const AppStack = createNativeStackNavigator<AppStackParamList>();

function AuthNavigator() {
  return (
    <AuthStack.Navigator screenOptions={{ headerShown: false }}>
      <AuthStack.Screen name="Login" component={LoginScreen} />
      <AuthStack.Screen
        name="Mfa"
        component={MfaScreen}
        options={{ headerShown: true, title: '2段階認証' }}
      />
    </AuthStack.Navigator>
  );
}

function AppNavigator() {
  return (
    <AppStack.Navigator>
      <AppStack.Screen
        name="TrainingList"
        component={TrainingListScreenLazy}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="Exercise"
        component={ExerciseScreenLazy}
        options={({ route }) => ({ title: route.params.menu })}
      />
      <AppStack.Screen
        name="TrainingStart"
        component={TrainingStartScreenLazy}
        options={{ title: 'トレーニング中', gestureEnabled: false }}
      />
      <AppStack.Screen
        name="AddExercise"
        component={AddExerciseScreenLazy}
        options={{ title: '種目を追加' }}
      />
      <AppStack.Screen
        name="Goal"
        component={GoalScreenLazy}
        options={{ headerShown: false, gestureEnabled: false }}
      />
      <AppStack.Screen
        name="Health"
        component={HealthScreenLazy}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="NoticeList"
        component={NoticeListScreenLazy}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="Withdrawal"
        component={WithdrawalScreenLazy}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="BodyMeasurement"
        component={BodyMeasurementScreenLazy}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="Profile"
        component={ProfileScreenLazy}
        options={{ headerShown: false }}
      />
    </AppStack.Navigator>
  );
}

type RootNavigatorProps = {
  initialRoute: 'Auth' | 'App';
};

export default function RootNavigator({ initialRoute }: RootNavigatorProps) {
  return (
    <NavigationContainer>
      <Root.Navigator
        initialRouteName={initialRoute}
        screenOptions={{ headerShown: false }}
      >
        <Root.Screen name="Auth" component={AuthNavigator} />
        <Root.Screen name="App" component={AppNavigator} />
      </Root.Navigator>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff',
  },
});
