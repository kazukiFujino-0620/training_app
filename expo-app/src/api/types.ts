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
  isAllCompleted: boolean;
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
  /** primitive boolean isPR → "PR"（2文字目も大文字のため decapitalize されない） */
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

// ── プッシュ通知 ───────────────────────────────────────────────────────────

export interface PushRegisterRequest {
  fcmToken: string;
  platform: 'ios' | 'android';
  deviceId: string;
}
