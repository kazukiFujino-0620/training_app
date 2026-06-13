import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import LoginScreen from '../screens/LoginScreen';
import MfaScreen from '../screens/MfaScreen';
import TrainingListScreen from '../screens/TrainingListScreen';
import ExerciseScreen from '../screens/ExerciseScreen';
import AddExerciseScreen from '../screens/AddExerciseScreen';
import TrainingStartScreen from '../screens/TrainingStartScreen';
import GoalScreen from '../screens/GoalScreen';

export type AuthStackParamList = {
  Login: undefined;
  Mfa: { mfaTempToken: string; deviceId: string };
};

export type AppStackParamList = {
  TrainingList: undefined;
  TrainingStart: undefined;
  Exercise: { trainingId: number; menu: string };
  AddExercise: undefined;
  Goal: {
    date: string;
    totalSets?: number;
    completedSets?: number;
    totalVolume?: number;
    sessionElapsed?: number;
  };
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
        component={TrainingListScreen}
        options={{ headerShown: false }}
      />
      <AppStack.Screen
        name="Exercise"
        component={ExerciseScreen}
        options={({ route }) => ({ title: route.params.menu })}
      />
      <AppStack.Screen
        name="TrainingStart"
        component={TrainingStartScreen}
        options={{ title: 'トレーニング中', gestureEnabled: false }}
      />
      <AppStack.Screen
        name="AddExercise"
        component={AddExerciseScreen}
        options={{ title: '種目を追加' }}
      />
      <AppStack.Screen
        name="Goal"
        component={GoalScreen}
        options={{ headerShown: false, gestureEnabled: false }}
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
