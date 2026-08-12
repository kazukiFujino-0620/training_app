import {
  HealthCaloriesRecord,
  HealthHeartRateRecord,
  HealthSleepRecord,
  HealthStepsRecord,
  HealthWeightRecord,
} from '../api/types';

/**
 * HealthKit/Health Connectを抽象化するアダプターのインターフェース。
 *
 * ita3-1要件定義で合意した「Google Fit APIリスクの軽減策」を実装したもの。
 * OS固有のヘルスケアAPIへの依存をこのインターフェースの背後に閉じ込めることで、
 * 将来Health Connect側にAPI変更があっても影響範囲をHealthConnectAdapterに限定できる。
 */
export interface HealthPlatformAdapter {
  /** この端末でヘルスケア連携が利用可能か（HealthKit非搭載iPad等を考慮）。 */
  isAvailable(): Promise<boolean>;

  /** 読み取り権限をリクエストする。ユーザーがOS側の許可ダイアログで操作する。 */
  requestPermissions(): Promise<boolean>;

  /** 指定期間の体重・体脂肪率を取得する。 */
  fetchWeight(startDate: Date, endDate: Date): Promise<HealthWeightRecord[]>;

  /** 指定期間の歩数（日別合計）を取得する。 */
  fetchSteps(startDate: Date, endDate: Date): Promise<HealthStepsRecord[]>;

  /** 指定期間の心拍数（日別平均・最小・最大）を取得する。 */
  fetchHeartRate(startDate: Date, endDate: Date): Promise<HealthHeartRateRecord[]>;

  /** 指定期間の消費カロリー（日別、アクティブ/合計）を取得する。 */
  fetchCalories(startDate: Date, endDate: Date): Promise<HealthCaloriesRecord[]>;

  /** 指定期間の睡眠記録を取得する。 */
  fetchSleep(startDate: Date, endDate: Date): Promise<HealthSleepRecord[]>;
}
