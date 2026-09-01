// ── 認証 ──────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
  deviceId: string;
}

export interface MfaVerifyRequest {
  mfaTempToken: string;
  deviceId: string;
  otp?: string;
  backupCode?: string;
}

export interface RefreshRequest {
  refreshToken: string;
  deviceId: string;
}

export interface TokenResponse {
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  mfaRequired: boolean;
  mfaTempToken?: string;
  /** itバグ-18: ホーム画面ヘッダーでのログインユーザー名表示に使う */
  userName?: string;
}

// ── トレーニング ───────────────────────────────────────────────────────────

export interface TrainingDetail {
  id: number;
  setNumber: number;
  setType: string;
  weight: number;
  reps: number;
  /** バックエンドの @JsonProperty("completed") に対応 */
  completed: boolean;
  /** 有酸素運動（ita2-1）の実施時間（分）。開始〜完了のタイマーから自動反映。筋トレ種目ではnull */
  durationMin: number | null;
  /** 有酸素運動（ita2-1）の距離（km）。マシンの表示値を手入力。筋トレ種目ではnull */
  distanceKm: number | null;
  /** 有酸素運動（ita2-1）の平均心拍数（bpm）。マシンの表示値を手入力。筋トレ種目ではnull */
  avgHeartRateBpm: number | null;
  /** 有酸素運動（ita2-1）の消費カロリー（kcal）。マシンの表示値を手入力。筋トレ種目ではnull */
  caloriesKcal: number | null;
}

export interface Training {
  id: number;
  menu: string;
  partCode: string;
  partName?: string;
  /** Jackson が isAllCompleted() ゲッターの "is" を剥がして allCompleted として返す */
  allCompleted: boolean;
  trainingDate: string;
  duration?: string;
  details: TrainingDetail[];
  /** スーパーセットグループID（F-M2）。NULL=単独種目、同値=同一グループ */
  supersetGroupId?: number | null;
  /** トレーニングメモ（ita4-4、500文字以内） */
  memo?: string | null;
}

/** 当日の推定消費カロリー（ita2-3）。全種目完了前、または計算対象データが無い場合はavailable=false */
export interface TrainingCalorieResponse {
  available: boolean;
  calories: number | null;
}

export interface AddSetRequest {
  weight: number;
  reps: number;
  setType?: string;
}

export interface AddTrainingRequest {
  menu: string;
  partCode: string;
  trainingDate?: string;
  memo?: string;
  sets: AddSetRequest[];
}

export interface SetUpdateRequest {
  weight?: number;
  reps?: number;
  isCompleted?: boolean;
  /** 有酸素運動（ita2-1）の実施時間（分）。開始〜完了のタイマーから自動反映 */
  durationMin?: number;
  /** 有酸素運動（ita2-1）の距離（km）。マシンの表示値を手入力 */
  distanceKm?: number;
  /** 有酸素運動（ita2-1）の平均心拍数（bpm）。マシンの表示値を手入力 */
  avgHeartRateBpm?: number;
  /** 有酸素運動（ita2-1）の消費カロリー（kcal）。マシンの表示値を手入力 */
  caloriesKcal?: number;
}

export interface SetUpdateResponse {
  id: number;
  /** primitive boolean → Lombok/Jackson が "is" を剥がして JSON キー "completed" になる */
  completed: boolean;
  /**
   * 自己ベスト（PR）更新フラグ。
   * 本来Jacksonの命名規則では isPR() → "pr"（全小文字）になるが、
   * バックエンド側で @JsonProperty("PR") によりキー名を "PR" に明示固定している。
   */
  PR: boolean;
  prMessage?: string;
  /**
   * 推奨インターバル秒数（F4）。重量/自己ベスト重量の比率から算出（60/90/180秒の3段階）。
   * 算出不可の場合は undefined。
   */
  recommendedIntervalSeconds?: number;
}

// ── 種目マスタ ─────────────────────────────────────────────────────────────

export interface TrainingItemMaster {
  id: number;
  partCode: string;
  itemName: string;
  displayOrder: number;
}

// ── トレーニング履歴（前回記録表示用） ──────────────────────────────────────

export interface TrainingHistorySet {
  setNo: number;
  weight: number;
  reps: number;
}

export interface TrainingHistory {
  date: string;
  sets: TrainingHistorySet[];
}

// ── プッシュ通知 ───────────────────────────────────────────────────────────

export interface PushRegisterRequest {
  fcmToken: string;
  platform: 'ios' | 'android';
  deviceId: string;
}

// ── ヘルスケア連動（ita3-1） ────────────────────────────────────────────────

export type HealthSyncSource = 'HEALTHKIT' | 'HEALTH_CONNECT';

export interface HealthWeightRecord {
  date: string;
  weightKg: number;
  bodyFatPct?: number;
}

export interface HealthStepsRecord {
  date: string;
  stepCount: number;
}

export interface HealthHeartRateRecord {
  date: string;
  avgBpm?: number;
  minBpm?: number;
  maxBpm?: number;
}

export interface HealthCaloriesRecord {
  date: string;
  activeCalories?: number;
  totalCalories?: number;
}

export interface HealthSleepRecord {
  date: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
}

export interface HealthSyncRequest {
  source: HealthSyncSource;
  weight?: HealthWeightRecord[];
  steps?: HealthStepsRecord[];
  heartRate?: HealthHeartRateRecord[];
  calories?: HealthCaloriesRecord[];
  sleep?: HealthSleepRecord[];
}

export interface HealthSyncResponse {
  syncedCount: number;
}

export interface HealthSummaryResponse {
  weight: { date: string; weightKg: number; bodyFatPct?: number; source: string } | null;
  steps: { date: string; stepCount: number; source: string } | null;
  heartRate:
    | { date: string; avgBpm?: number; minBpm?: number; maxBpm?: number; source: string }
    | null;
  calories:
    | { date: string; activeCalories?: number; totalCalories?: number; source: string }
    | null;
  sleep:
    | {
        date: string;
        startTime: string;
        endTime: string;
        durationMinutes: number;
        source: string;
      }
    | null;
}

/** ジム・店舗からのお知らせ（ita2-5）。閲覧（一覧画面表示）した時点でdismiss扱いとなり以後表示されなくなる */
export interface Notice {
  id: number;
  organizationId: number;
  title: string;
  body: string;
  createdBy: number;
  createdAt: string;
  deletedAt: string | null;
}

// ── AIトレーニング提案（ita5-1 機能1・仮連携） ────────────────────────────

export interface AiSuggestedItem {
  itemName: string;
  weightMin: number;
  weightMax: number;
  repsMin: number;
  repsMax: number;
  sets: number;
}

export interface AiTrainingSuggestion {
  comment: string;
  partCode: string | null;
  items: AiSuggestedItem[];
}
