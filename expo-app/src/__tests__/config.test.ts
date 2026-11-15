describe('config の接続先（itバグ-33）', () => {
  const original = process.env.EXPO_PUBLIC_API_URL;
  let warnSpy: jest.SpyInstance;

  beforeEach(() => {
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    warnSpy.mockRestore();
    if (original === undefined) {
      delete process.env.EXPO_PUBLIC_API_URL;
    } else {
      process.env.EXPO_PUBLIC_API_URL = original;
    }
  });

  function loadConfig(apiUrl: string | undefined): typeof import('../config') {
    if (apiUrl === undefined) {
      delete process.env.EXPO_PUBLIC_API_URL;
    } else {
      process.env.EXPO_PUBLIC_API_URL = apiUrl;
    }
    let config!: typeof import('../config');
    jest.isolateModules(() => {
      config = require('../config');
    });
    return config;
  }

  it('本番ドメインのURLだけを本番と判定する', () => {
    const { isProductionApiUrl } = loadConfig(undefined);
    expect(isProductionApiUrl('https://training-app-test.mydns.jp/api/mobile')).toBe(true);
    expect(isProductionApiUrl('https://Training-App-Test.mydns.jp:443/api/mobile')).toBe(true);
    expect(isProductionApiUrl('http://localhost:8080/api/mobile')).toBe(false);
    expect(isProductionApiUrl('http://192.168.11.11:8080/api/mobile')).toBe(false);
    expect(isProductionApiUrl('https://xxxx.ngrok-free.app/api/mobile')).toBe(false);
    expect(isProductionApiUrl('https://training-app-test.mydns.jp.example.com/api/mobile')).toBe(false);
  });

  it('未設定ならローカルにつなぎ、警告は出さない', () => {
    const config = loadConfig(undefined);
    expect(config.API_BASE_URL).toBe('http://localhost:8080/api/mobile');
    expect(config.SERVER_ORIGIN).toBe('http://localhost:8080');
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it('開発ビルドで本番URLを指していたら起動時に警告する', () => {
    const config = loadConfig('https://training-app-test.mydns.jp/api/mobile');
    expect(config.API_BASE_URL).toBe('https://training-app-test.mydns.jp/api/mobile');
    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy.mock.calls[0][0]).toContain('本番サーバー');
  });
});
