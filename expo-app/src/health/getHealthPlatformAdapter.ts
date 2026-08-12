import { Platform } from 'react-native';
import { HealthConnectAdapter } from './HealthConnectAdapter';
import { HealthKitAdapter } from './HealthKitAdapter';
import { HealthPlatformAdapter } from './types';

/** 実行中のOSに応じたHealthKit/Health Connectアダプターを返す。 */
export function getHealthPlatformAdapter(): HealthPlatformAdapter | null {
  if (Platform.OS === 'ios') {
    return new HealthKitAdapter();
  }
  if (Platform.OS === 'android') {
    return new HealthConnectAdapter();
  }
  return null;
}
