import React from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { AppStackParamList } from '../navigation/AppNavigator';

type Props = {
  navigation: NativeStackNavigationProp<AppStackParamList, 'Goal'>;
  route: RouteProp<AppStackParamList, 'Goal'>;
};

export default function GoalScreen({ navigation, route }: Props) {
  const { totalSets = 0, completedSets = 0, totalVolume = 0 } = route.params;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <Text style={styles.trophy}>🏆</Text>
        <Text style={styles.title}>トレーニング完了！</Text>
        <Text style={styles.subtitle}>お疲れさまでした</Text>

        <View style={styles.card}>
          <StatRow label="完了セット数" value={`${completedSets} セット`} />
          <StatRow label="総ボリューム" value={`${totalVolume.toLocaleString()} kg`} />
        </View>

        <TouchableOpacity
          style={styles.button}
          onPress={() => navigation.navigate('TrainingList')}
        >
          <Text style={styles.buttonText}>ホームに戻る</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={statStyles.row}>
      <Text style={statStyles.label}>{label}</Text>
      <Text style={statStyles.value}>{value}</Text>
    </View>
  );
}

const statStyles = StyleSheet.create({
  row: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    paddingVertical: 12, borderBottomWidth: 1, borderBottomColor: '#f0f0f0',
  },
  label: { fontSize: 14, color: '#666' },
  value: { fontSize: 18, fontWeight: '700', color: '#222' },
});

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#fff' },
  container: {
    flex: 1, justifyContent: 'center', alignItems: 'center', paddingHorizontal: 32,
  },
  trophy: { fontSize: 80, marginBottom: 16 },
  title: { fontSize: 28, fontWeight: '900', color: '#222', marginBottom: 8 },
  subtitle: { fontSize: 16, color: '#888', marginBottom: 40 },
  card: {
    width: '100%', backgroundColor: '#f9f9f9', borderRadius: 16,
    padding: 20, marginBottom: 32,
  },
  button: {
    width: '100%', backgroundColor: '#4CAF50', borderRadius: 12,
    padding: 16, alignItems: 'center',
  },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
