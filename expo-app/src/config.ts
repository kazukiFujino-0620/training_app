// 接続先は expo-app/.env.development（開発時: Metro）と .env.production（リリースビルド時）で切り替える。
// 開発時に別の接続先（実機確認用の LAN IP・ngrok 等）を使う場合は .env.development.local に書く
// （.env.local はリリースビルドでも .env.production より優先されるため使わない）。
// 開発時のバンドルでは .env* ファイルの値がシェルの環境変数より優先されるため、
// `EXPO_PUBLIC_API_URL=... npx expo start` のようにシェルで渡しても切り替わらない（itバグ-33）。
export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api/mobile';

// Google/LINEログイン（/mobile-oauth/**）はAPI_BASE_URLの/api/mobile配下ではなくサーバー直下にあるため、
// サフィックスを除いたオリジンを別途持つ。
export const SERVER_ORIGIN = API_BASE_URL.replace(/\/api\/mobile\/?$/, '');

const PRODUCTION_HOST = 'training-app-test.mydns.jp';

export function isProductionApiUrl(url: string): boolean {
  const host = /^https?:\/\/([^/:?#]+)/i.exec(url)?.[1];
  return host?.toLowerCase() === PRODUCTION_HOST;
}

// 開発ビルドが本番につながっていることに気づけるよう、起動時に警告を出す
if (__DEV__ && isProductionApiUrl(API_BASE_URL)) {
  console.warn(
    `[接続先注意] 開発ビルドが本番サーバー（${API_BASE_URL}）に接続しています。` +
      'ローカルで確認する場合は expo-app/.env.development.local などの EXPO_PUBLIC_API_URL を確認してください。'
  );
}
