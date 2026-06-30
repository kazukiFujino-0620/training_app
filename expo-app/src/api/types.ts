// ── 認証 ──────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string;
  password: string;
  deviceId: string;
}

export interface MfaVerifyRequest {
  mfaTempToken: string;
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
