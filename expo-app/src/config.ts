// 開発時: Expo Go から物理端末で接続する場合は localhost ではなく PC の LAN IP を設定する
// 例: http://192.168.1.10:8080/api/mobile
// GCP 本番: https://api.yourdomain.com/api/mobile
export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api/mobile';
