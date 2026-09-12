import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config';
import { getTokens, saveTokens, clearTokens } from '../auth/tokenStore';

const client = axios.create({ baseURL: API_BASE_URL, timeout: 15000 });

// ── リクエスト: Authorization ヘッダーを付与 ────────────────────────────────
client.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const { accessToken } = await getTokens();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// ── レスポンス: 401 時にリフレッシュしてリトライ ──────────────────────────────
let isRefreshing = false;
let refreshQueue: Array<(token: string) => void> = [];

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;

    if (isRefreshing) {
      // 他のリクエストがリフレッシュ中なら完了を待つ
      return new Promise((resolve, reject) => {
        refreshQueue.push((newToken) => {
          original.headers.Authorization = `Bearer ${newToken}`;
          resolve(client(original));
        });
      });
    }

    isRefreshing = true;
    try {
      const { refreshToken, deviceId } = await getTokens();
      if (!refreshToken || !deviceId) throw new Error('no refresh token');

      const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, {
        refreshToken,
        deviceId,
      });

      await saveTokens(data.accessToken, data.refreshToken, deviceId, data.userName);
      refreshQueue.forEach((cb) => cb(data.accessToken));
      refreshQueue = [];
      original.headers.Authorization = `Bearer ${data.accessToken}`;
      return client(original);
    } catch {
      await clearTokens();
      refreshQueue = [];
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  },
);

export default client;

// ── API 関数 ───────────────────────────────────────────────────────────────

import type {
  LoginRequest,
  MfaVerifyRequest,
  TokenResponse,
  Training,
  TrainingDetail,
  AddSetRequest,
  AddTrainingRequest,
  SetUpdateRequest,
  SetUpdateResponse,
  TrainingItemMaster,
  TrainingHistory,
  PushRegisterRequest,
  HealthSyncRequest,
  HealthSyncResponse,
  HealthSummaryResponse,
  TrainingCalorieResponse,
  Notice,
  AiTrainingSuggestion,
  DailyRecommendation,
  WithdrawalStatus,
  BodyMeasurement,
  SaveBodyMeasurementRequest,
  MobileProfile,
  UpdateProfileRequest,
  MobileTrainingStatsResponse,
} from './types';

export const authApi = {
  login: (req: LoginRequest) =>
    client.post<TokenResponse>('/auth/login', req),
  mfaVerify: (req: MfaVerifyRequest) =>
    client.post<TokenResponse>('/auth/mfa/verify', req),
  refresh: (refreshToken: string, deviceId: string) =>
    client.post<TokenResponse>('/auth/refresh', { refreshToken, deviceId }),
  logout: (deviceId: string, refreshToken: string) =>
    client.post('/auth/logout', { deviceId, refreshToken }),
};

export const trainingApi = {
  getToday: (date?: string) =>
    client.get<Training[]>('/training/today', { params: date ? { date } : undefined }),
  getTodayCalories: (date?: string) =>
    client.get<TrainingCalorieResponse>('/training/today/calories', {
      params: date ? { date } : undefined,
    }),
  addTraining: (req: AddTrainingRequest) =>
    client.post<number>('/training', req),
  deleteTraining: (id: number) =>
    client.delete(`/training/${id}`),
  updateSet: (id: number, req: SetUpdateRequest) =>
    client.patch<SetUpdateResponse>(`/training/sets/${id}`, req),
  updateMemo: (id: number, memo: string) =>
    client.patch(`/training/${id}/memo`, { memo }),
  /** ita7-1 1-1: トレーニング本体（種目名・部位・日付）の更新（過去分編集対応） */
  updateTraining: (id: number, req: { menu: string; partCode: string; trainingDate: string }) =>
    client.patch(`/training/${id}`, req),
  completeTraining: (trainingId: number, durationSec?: number) =>
    client.post('/training/complete', { trainingId, durationSec }),
  addSet: (trainingId: number, req: AddSetRequest) =>
    client.post<TrainingDetail>(`/training/${trainingId}/sets`, req),
  deleteSet: (id: number) =>
    client.delete(`/training/sets/${id}`),
  getTrainingHistory: (itemName: string) =>
    client.get<TrainingHistory[]>('/training/history', { params: { itemName } }),
  groupSuperset: (trainingIds: number[]) =>
    client.post<{ supersetGroupId: number }>('/training/superset/group', { trainingIds }),
  ungroupSuperset: (supersetGroupId: number) =>
    client.post('/training/superset/ungroup', { supersetGroupId }),
  /** itバグ-10: トレーニング順の変更。当日の対象トレーニング全件のIDを希望の並び順で渡す */
  reorder: (orderedIds: number[]) =>
    client.post('/training/reorder', orderedIds),
};

export const coachingApi = {
  // itバグ-21対応（2026-09-11）: 「（モック）」文言のためTrainingListScreenからの呼び出しは
  // 廃止した（recommendationApi.getToday()に差し替え済み）。ita5-1の本番AI連携が稼働した際に
  // 同じ枠へ戻す2段階移行のため、API自体は削除せず残す。
  /** ita5-1 機能1（仮連携）: 当日のAIトレーニング提案。同意していない/提案が無い場合は204（dataはundefined）。 */
  getTodayTrainingSuggestion: () =>
    client.get<AiTrainingSuggestion>('/coaching/training-suggestion/today'),
};

/** F3 Phase1: 今日のおすすめメニュー（ルールベース推奨）。itバグ-21でモバイル「トレーニング」タブの
 * AI提案（モック）カードをこちらに差し替えた。 */
export const recommendationApi = {
  getToday: () => client.get<DailyRecommendation>('/recommendations/today'),
};

export const masterApi = {
  getItems: (partCode?: string) =>
    client.get<TrainingItemMaster[]>('/master/items', {
      params: partCode ? { partCode } : undefined,
    }),
};

export const noticeApi = {
  getActive: () => client.get<Notice[]>('/notices/active'),
  dismiss: (id: number) => client.post(`/notices/${id}/dismiss`),
};

export const pushApi = {
  register: (req: PushRegisterRequest) =>
    client.post('/push/register', req),
  unregister: (req: PushRegisterRequest) =>
    client.delete('/push/unregister', { data: req }),
};

export const healthApi = {
  sync: (req: HealthSyncRequest) =>
    client.post<HealthSyncResponse>('/health/sync', req),
  getSummary: () => client.get<HealthSummaryResponse>('/health/summary'),
};

/** ita7-1 1-2: 体重・体脂肪率の手動記録 */
export const bodyMeasurementApi = {
  getAll: () => client.get<BodyMeasurement[]>('/body'),
  save: (req: SaveBodyMeasurementRequest) => client.post('/body', req),
  delete: (id: number) => client.delete(`/body/${id}`),
};

/** ita7-1 1-3: プロフィール編集 */
export const profileApi = {
  get: () => client.get<MobileProfile>('/profile'),
  update: (req: UpdateProfileRequest) => client.patch('/profile', req),
  updateGoalMode: (goalMode: string) => client.patch('/profile/goal-mode', { goalMode }),
  updateAiAdviceConsent: (aiAdviceConsent: boolean) =>
    client.patch('/profile/ai-advice-consent', { aiAdviceConsent }),
};

/** ita7-2: カレンダータブ下の統計バー（今月・先週比・今週の部位・今日の予定） */
export const statsApi = {
  getTraining: () => client.get<MobileTrainingStatsResponse>('/stats/training'),
};

export const withdrawalApi = {
  /** 一般ユーザーか（即時削除フロー）、ジム所属ユーザーか（申請制フロー）、申請中かを取得する。 */
  getStatus: () => client.get<WithdrawalStatus>('/withdrawal/status'),
  /** ジム所属ユーザー向け：退会を申請する（管理者承認後に削除）。 */
  request: (reasonType?: string, reasonText?: string) =>
    client.post('/withdrawal/request', { reasonType, reasonText }),
  /** ジム所属ユーザー向け：申請中の退会申請をキャンセルする。 */
  cancel: () => client.post('/withdrawal/cancel'),
  /** 一般ユーザー向け：申請を挟まず即座にアカウント・データを削除する。 */
  deleteImmediately: () => client.post('/withdrawal/delete-immediately'),
};
