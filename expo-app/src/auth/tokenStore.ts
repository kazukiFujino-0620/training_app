import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';

const KEY_ACCESS  = 'access_token';
const KEY_REFRESH = 'refresh_token';
const KEY_DEVICE  = 'device_id';

export interface StoredTokens {
  accessToken: string | null;
  refreshToken: string | null;
  deviceId: string | null;
}

export async function getTokens(): Promise<StoredTokens> {
  const [accessToken, refreshToken, deviceId] = await Promise.all([
    SecureStore.getItemAsync(KEY_ACCESS),
    SecureStore.getItemAsync(KEY_REFRESH),
    SecureStore.getItemAsync(KEY_DEVICE),
  ]);
  return { accessToken, refreshToken, deviceId };
}

export async function saveTokens(
  accessToken: string,
  refreshToken: string,
  deviceId: string,
): Promise<void> {
  await Promise.all([
    SecureStore.setItemAsync(KEY_ACCESS, accessToken),
    SecureStore.setItemAsync(KEY_REFRESH, refreshToken),
    SecureStore.setItemAsync(KEY_DEVICE, deviceId),
  ]);
}

export async function clearTokens(): Promise<void> {
  await Promise.all([
    SecureStore.deleteItemAsync(KEY_ACCESS),
    SecureStore.deleteItemAsync(KEY_REFRESH),
    SecureStore.deleteItemAsync(KEY_DEVICE),
  ]);
}

export async function getOrCreateDeviceId(): Promise<string> {
  let deviceId = await SecureStore.getItemAsync(KEY_DEVICE);
  if (!deviceId) {
    deviceId = Crypto.randomUUID();
    await SecureStore.setItemAsync(KEY_DEVICE, deviceId);
  }
  return deviceId;
}
