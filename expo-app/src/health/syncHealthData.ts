import { Platform } from 'react-native';
import { healthApi } from '../api/client';
import { HealthSyncSource } from '../api/types';
import { getHealthPlatformAdapter } from './getHealthPlatformAdapter';

/** 直近何日分を同期するかのデフォルト（初回・通常同期とも同じ範囲でシンプルに保つ）。 */
const SYNC_DAYS = 30;

export interface SyncResult {
  ok: boolean;
  syncedCount: number;
  reason?: string;
}

/**
 * OS側のHealthKit/Health Connectから直近{@link SYNC_DAYS}日分のデータを取得し、
 * バックエンドの `/health/sync` へ送信する。読み取りのみ（書き込みは行わない）。
 */
export async function syncHealthData(): Promise<SyncResult> {
  const adapter = getHealthPlatformAdapter();
  if (!adapter) {
    return { ok: false, syncedCount: 0, reason: 'unsupported platform' };
  }

  const available = await adapter.isAvailable();
  if (!available) {
    return { ok: false, syncedCount: 0, reason: 'health platform not available on this device' };
  }

  const granted = await adapter.requestPermissions();
  if (!granted) {
    return { ok: false, syncedCount: 0, reason: 'permission denied' };
  }

  const endDate = new Date();
  const startDate = new Date(endDate);
  startDate.setDate(startDate.getDate() - SYNC_DAYS);

  const [weight, steps, heartRate, calories, sleep] = await Promise.all([
    adapter.fetchWeight(startDate, endDate),
    adapter.fetchSteps(startDate, endDate),
    adapter.fetchHeartRate(startDate, endDate),
    adapter.fetchCalories(startDate, endDate),
    adapter.fetchSleep(startDate, endDate),
  ]);

  const source: HealthSyncSource = Platform.OS === 'ios' ? 'HEALTHKIT' : 'HEALTH_CONNECT';

  const response = await healthApi.sync({
    source,
    weight,
    steps,
    heartRate,
    calories,
    sleep,
  });

  return { ok: true, syncedCount: response.data.syncedCount };
}
