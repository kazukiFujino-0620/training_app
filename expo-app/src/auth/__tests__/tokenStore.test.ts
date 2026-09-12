import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';
import { clearTokens, getOrCreateDeviceId, saveTokens, getUserName } from '../tokenStore';

// SEC-M1 / SEC-M2 単体テスト
// 対象: src/auth/tokenStore.ts
// 参照: training-app/単体テスト/SEC-M1_SEC-M2_tokenStore_テスト仕様書.md

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('expo-crypto', () => ({
  randomUUID: jest.fn(),
}));

const store: Record<string, string> = {};

beforeEach(() => {
  jest.clearAllMocks();
  Object.keys(store).forEach((k) => delete store[k]);

  (SecureStore.getItemAsync as jest.Mock).mockImplementation(
    async (key: string) => store[key] ?? null,
  );
  (SecureStore.setItemAsync as jest.Mock).mockImplementation(async (key: string, value: string) => {
    store[key] = value;
  });
  (SecureStore.deleteItemAsync as jest.Mock).mockImplementation(async (key: string) => {
    delete store[key];
  });

  let seq = 0;
  (Crypto.randomUUID as jest.Mock).mockImplementation(() => `uuid-${++seq}`);
});

describe('SEC-M1: getOrCreateDeviceId() の乱数生成', () => {
  it('TC-1: deviceId未生成時、Crypto.randomUUID()で生成し保存する', async () => {
    const deviceId = await getOrCreateDeviceId();

    expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
    expect(deviceId).toBe('uuid-1');
    expect(SecureStore.setItemAsync).toHaveBeenCalledWith('device_id', 'uuid-1');
  });

  it('TC-2: 既存のdeviceIdがある場合は再生成せずそのまま返す', async () => {
    store['device_id'] = 'existing-device-id';

    const deviceId = await getOrCreateDeviceId();

    expect(deviceId).toBe('existing-device-id');
    expect(Crypto.randomUUID).not.toHaveBeenCalled();
    expect(SecureStore.setItemAsync).not.toHaveBeenCalled();
  });

  it('TC-3: Math.random ベースの生成に戻っていないことの回帰確認', async () => {
    const mathRandomSpy = jest.spyOn(Math, 'random');

    await getOrCreateDeviceId();

    expect(mathRandomSpy).not.toHaveBeenCalled();
    mathRandomSpy.mockRestore();
  });
});

describe('SEC-M2: clearTokens() のdevice_id削除（案A）', () => {
  it('TC-4: clearTokens()はaccess_token・refresh_tokenに加えdevice_idも削除する', async () => {
    store['access_token'] = 'a';
    store['refresh_token'] = 'r';
    store['device_id'] = 'd';

    await clearTokens();

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('access_token');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('refresh_token');
    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('device_id');
    expect(store['device_id']).toBeUndefined();
  });

  it('TC-5: clearTokens()実行後にgetOrCreateDeviceId()を呼ぶと新しいdeviceIdが生成される', async () => {
    store['device_id'] = 'old-device-id';

    await clearTokens();
    const deviceId = await getOrCreateDeviceId();

    expect(deviceId).not.toBe('old-device-id');
    expect(Crypto.randomUUID).toHaveBeenCalledTimes(1);
  });
});

describe('itバグ-18: ログインユーザー名の保存・取得・削除', () => {
  it('TC-6: saveTokensにuserNameを渡すと保存され、getUserName()で取得できる', async () => {
    await saveTokens('access', 'refresh', 'device', '山田太郎');

    expect(await getUserName()).toBe('山田太郎');
  });

  it('TC-7: userNameを渡さない場合は保存されない（既存値も上書きしない）', async () => {
    store['user_name'] = '既存ユーザー';

    await saveTokens('access', 'refresh', 'device');

    expect(await getUserName()).toBe('既存ユーザー');
  });

  it('TC-8: clearTokens()はuser_nameも削除する', async () => {
    store['user_name'] = '山田太郎';

    await clearTokens();

    expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith('user_name');
    expect(await getUserName()).toBeNull();
  });
});
