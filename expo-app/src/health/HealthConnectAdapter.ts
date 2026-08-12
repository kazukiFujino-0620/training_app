import {
  getSdkStatus,
  initialize,
  readRecords,
  requestPermission,
  SdkAvailabilityStatus,
} from 'react-native-health-connect';
import {
  HealthCaloriesRecord,
  HealthHeartRateRecord,
  HealthSleepRecord,
  HealthStepsRecord,
  HealthWeightRecord,
} from '../api/types';
import { HealthPlatformAdapter } from './types';

const REQUIRED_PERMISSIONS: { accessType: 'read'; recordType: string }[] = [
  { accessType: 'read', recordType: 'Weight' },
  { accessType: 'read', recordType: 'Steps' },
  { accessType: 'read', recordType: 'HeartRate' },
  { accessType: 'read', recordType: 'ActiveCaloriesBurned' },
  { accessType: 'read', recordType: 'TotalCaloriesBurned' },
  { accessType: 'read', recordType: 'SleepSession' },
];

function toDateOnly(iso: string): string {
  return iso.slice(0, 10);
}

/** react-native-health-connect経由のAndroid Health Connectアダプター。 */
export class HealthConnectAdapter implements HealthPlatformAdapter {
  async isAvailable(): Promise<boolean> {
    const status = await getSdkStatus();
    return status === SdkAvailabilityStatus.SDK_AVAILABLE;
  }

  async requestPermissions(): Promise<boolean> {
    const initialized = await initialize();
    if (!initialized) return false;
    const granted = await requestPermission(REQUIRED_PERMISSIONS as never);
    return granted.length > 0;
  }

  async fetchWeight(startDate: Date, endDate: Date): Promise<HealthWeightRecord[]> {
    const { records } = await readRecords('Weight', {
      timeRangeFilter: {
        operator: 'between',
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
      },
    });
    // readRecordsはMassResult（inKilograms等、単位変換済み）を返すため手動変換は不要
    const byDate = new Map<string, (typeof records)[number]>();
    for (const r of records) {
      byDate.set(toDateOnly(r.time), r);
    }
    return Array.from(byDate.entries()).map(([date, r]) => ({
      date,
      weightKg: Math.round(r.weight.inKilograms * 100) / 100,
    }));
  }

  async fetchSteps(startDate: Date, endDate: Date): Promise<HealthStepsRecord[]> {
    const { records } = await readRecords('Steps', {
      timeRangeFilter: {
        operator: 'between',
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
      },
    });
    const byDate = new Map<string, number>();
    for (const r of records) {
      const date = toDateOnly(r.startTime);
      byDate.set(date, (byDate.get(date) ?? 0) + r.count);
    }
    return Array.from(byDate.entries()).map(([date, stepCount]) => ({ date, stepCount }));
  }

  async fetchHeartRate(startDate: Date, endDate: Date): Promise<HealthHeartRateRecord[]> {
    const { records } = await readRecords('HeartRate', {
      timeRangeFilter: {
        operator: 'between',
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
      },
    });
    const byDate = new Map<string, number[]>();
    for (const r of records) {
      const date = toDateOnly(r.startTime);
      const bpms = r.samples.map((s) => s.beatsPerMinute);
      byDate.set(date, [...(byDate.get(date) ?? []), ...bpms]);
    }
    return Array.from(byDate.entries()).map(([date, values]) => ({
      date,
      avgBpm: Math.round(values.reduce((a, b) => a + b, 0) / values.length),
      minBpm: Math.round(Math.min(...values)),
      maxBpm: Math.round(Math.max(...values)),
    }));
  }

  async fetchCalories(startDate: Date, endDate: Date): Promise<HealthCaloriesRecord[]> {
    const timeRangeFilter = {
      operator: 'between' as const,
      startTime: startDate.toISOString(),
      endTime: endDate.toISOString(),
    };
    // readRecordsはEnergyResult（inKilocalories等、単位変換済み）を返すため手動変換は不要
    const [active, total] = await Promise.all([
      readRecords('ActiveCaloriesBurned', { timeRangeFilter }),
      readRecords('TotalCaloriesBurned', { timeRangeFilter }),
    ]);

    const activeByDate = new Map<string, number>();
    for (const r of active.records) {
      const date = toDateOnly(r.startTime);
      activeByDate.set(date, (activeByDate.get(date) ?? 0) + r.energy.inKilocalories);
    }
    const totalByDate = new Map<string, number>();
    for (const r of total.records) {
      const date = toDateOnly(r.startTime);
      totalByDate.set(date, (totalByDate.get(date) ?? 0) + r.energy.inKilocalories);
    }

    const dates = new Set([...activeByDate.keys(), ...totalByDate.keys()]);
    return Array.from(dates).map((date) => ({
      date,
      activeCalories: activeByDate.get(date),
      totalCalories: totalByDate.get(date),
    }));
  }

  async fetchSleep(startDate: Date, endDate: Date): Promise<HealthSleepRecord[]> {
    const { records } = await readRecords('SleepSession', {
      timeRangeFilter: {
        operator: 'between',
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
      },
    });
    // 起床日（endTimeの日付）が同じセッションのうち最長のものを採用
    const byWakeDate = new Map<string, (typeof records)[number]>();
    for (const r of records) {
      const wakeDate = toDateOnly(r.endTime);
      const existing = byWakeDate.get(wakeDate);
      if (!existing) {
        byWakeDate.set(wakeDate, r);
        continue;
      }
      const existingDuration =
        new Date(existing.endTime).getTime() - new Date(existing.startTime).getTime();
      const currentDuration = new Date(r.endTime).getTime() - new Date(r.startTime).getTime();
      if (currentDuration > existingDuration) {
        byWakeDate.set(wakeDate, r);
      }
    }
    return Array.from(byWakeDate.entries()).map(([date, r]) => ({
      date,
      startTime: r.startTime,
      endTime: r.endTime,
      durationMinutes: Math.round(
        (new Date(r.endTime).getTime() - new Date(r.startTime).getTime()) / 60000,
      ),
    }));
  }
}
