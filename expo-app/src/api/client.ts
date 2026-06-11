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

      await saveTokens(data.accessToken, data.refreshToken, deviceId);
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
  PushRegisterRequest,
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
  addTraining: (req: AddTrainingRequest) =>
    client.post<number>('/training', req),
  deleteTraining: (id: number) =>
    client.delete(`/training/${id}`),
  updateSet: (id: number, req: SetUpdateRequest) =>
    client.patch<SetUpdateResponse>(`/training/sets/${id}`, req),
  completeTraining: (trainingId: number) =>
    client.post('/training/complete', { trainingId }),
  addSet: (trainingId: number, req: AddSetRequest) =>
    client.post<TrainingDetail>(`/training/${trainingId}/sets`, req),
  deleteSet: (id: number) =>
    client.delete(`/training/sets/${id}`),
};

export const masterApi = {
  getItems: (partCode?: string) =>
    client.get<TrainingItemMaster[]>('/master/items', {
      params: partCode ? { partCode } : undefined,
    }),
};

export const pushApi = {
  register: (req: PushRegisterRequest) =>
    client.post('/push/register', req),
  unregister: (req: PushRegisterRequest) =>
    client.delete('/push/unregister', { data: req }),
};
