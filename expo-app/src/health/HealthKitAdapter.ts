import {
  CategoryValueSleepAnalysis,
  isHealthDataAvailableAsync,
  queryCategorySamples,
  queryQuantitySamples,
  requestAuthorization,
} from '@kingstinct/react-native-healthkit';
import {
  HealthCaloriesRecord,
  HealthHeartRateRecord,
  HealthSleepRecord,
  HealthStepsRecord,
  HealthWeightRecord,
} from '../api/types';
import { HealthPlatformAdapter } from './types';

const READ_IDENTIFIERS = [
  'HKQuantityTypeIdentifierBodyMass',
  'HKQuantityTypeIdentifierStepCount',
  'HKQuantityTypeIdentifierHeartRate',
  'HKQuantityTypeIdentifierActiveEnergyBurned',
  'HKCategoryTypeIdentifierSleepAnalysis',
] as const;

function toDateOnly(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** @kingstinct/react-native-healthkit 経由のiOS HealthKitアダプター（New Architecture対応）。 */
export class HealthKitAdapter implements HealthPlatformAdapter {
  async isAvailable(): Promise<boolean> {
    return isHealthDataAvailableAsync();
  }

  async requestPermissions(): Promise<boolean> {
    return requestAuthorization({ toRead: READ_IDENTIFIERS as unknown as any });
  }

  async fetchWeight(startDate: Date, endDate: Date): Promise<HealthWeightRecord[]> {
    const samples = await queryQuantitySamples('HKQuantityTypeIdentifierBodyMass', {
      filter: { date: { startDate, endDate } },
      unit: 'kg',
      limit: 0,
      ascending: true,
    });
    const byDate = new Map<string, (typeof samples)[number]>();
    for (const s of samples) {
      byDate.set(toDateOnly(s.startDate), s);
    }
    return Array.from(byDate.entries()).map(([date, s]) => ({
      date,
      weightKg: Math.round(s.quantity * 100) / 100,
    }));
  }

  async fetchSteps(startDate: Date, endDate: Date): Promise<HealthStepsRecord[]> {
    const samples = await queryQuantitySamples('HKQuantityTypeIdentifierStepCount', {
      filter: { date: { startDate, endDate } },
      unit: 'count',
      limit: 0,
    });
    const byDate = new Map<string, number>();
    for (const s of samples) {
      const date = toDateOnly(s.startDate);
      byDate.set(date, (byDate.get(date) ?? 0) + s.quantity);
    }
    return Array.from(byDate.entries()).map(([date, stepCount]) => ({
      date,
      stepCount: Math.round(stepCount),
    }));
  }

  async fetchHeartRate(startDate: Date, endDate: Date): Promise<HealthHeartRateRecord[]> {
    const samples = await queryQuantitySamples('HKQuantityTypeIdentifierHeartRate', {
      filter: { date: { startDate, endDate } },
      unit: 'count/min',
      limit: 0,
    });
    const byDate = new Map<string, number[]>();
    for (const s of samples) {
      const date = toDateOnly(s.startDate);
      byDate.set(date, [...(byDate.get(date) ?? []), s.quantity]);
    }
    return Array.from(byDate.entries()).map(([date, values]) => ({
      date,
      avgBpm: Math.round(values.reduce((a, b) => a + b, 0) / values.length),
      minBpm: Math.round(Math.min(...values)),
      maxBpm: Math.round(Math.max(...values)),
    }));
  }

  async fetchCalories(startDate: Date, endDate: Date): Promise<HealthCaloriesRecord[]> {
    const samples = await queryQuantitySamples('HKQuantityTypeIdentifierActiveEnergyBurned', {
      filter: { date: { startDate, endDate } },
      unit: 'kcal',
      limit: 0,
    });
    const byDate = new Map<string, number>();
    for (const s of samples) {
      const date = toDateOnly(s.startDate);
      byDate.set(date, (byDate.get(date) ?? 0) + s.quantity);
    }
    // HealthKitに「合計消費カロリー」という単一指標は無いため、アクティブ消費のみ計上する
    // （安静時消費(BasalEnergyBurned)は本課題のスコープ外）。
    return Array.from(byDate.entries()).map(([date, activeCalories]) => ({
      date,
      activeCalories: Math.round(activeCalories * 100) / 100,
    }));
  }

  async fetchSleep(startDate: Date, endDate: Date): Promise<HealthSleepRecord[]> {
    const samples = await queryCategorySamples('HKCategoryTypeIdentifierSleepAnalysis', {
      filter: { date: { startDate, endDate } },
      limit: 0,
    });
    // 「ベッドにいる(inBed)」「覚醒(awake)」を除いた実睡眠ステージのみ集計対象とする
    const asleepSamples = samples.filter(
      (s) =>
        s.value !== CategoryValueSleepAnalysis.inBed &&
        s.value !== CategoryValueSleepAnalysis.awake,
    );

    const byWakeDate = new Map<string, { start: Date; end: Date; durationMs: number }>();
    for (const s of asleepSamples) {
      const wakeDate = toDateOnly(s.endDate);
      const durationMs = s.endDate.getTime() - s.startDate.getTime();
      const existing = byWakeDate.get(wakeDate);
      if (!existing) {
        byWakeDate.set(wakeDate, { start: s.startDate, end: s.endDate, durationMs });
      } else {
        byWakeDate.set(wakeDate, {
          start: s.startDate < existing.start ? s.startDate : existing.start,
          end: s.endDate > existing.end ? s.endDate : existing.end,
          durationMs: existing.durationMs + durationMs,
        });
      }
    }

    return Array.from(byWakeDate.entries()).map(([date, { start, end, durationMs }]) => ({
      date,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      durationMinutes: Math.round(durationMs / 60000),
    }));
  }
}
