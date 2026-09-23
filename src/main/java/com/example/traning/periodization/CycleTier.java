package com.example.traning.periodization;

/** program_cycles.tier。専用レベル列は持たず、サイクルの作り方で行動ベースに判定する（QA Q3-6、2026-09-23見直し）。 */
public enum CycleTier {
  /** 目的別の初心者向けプリセットを採用した。採用後に曜日割当・種目を編集してもこのまま。 */
  BEGINNER_PRESET,
  /** プリセットを使わず白紙から自分で組んだ（createCustomCycle）。 */
  INTERMEDIATE_CUSTOM,
  /** トレーナーが担当ユーザー向けに作成した。 */
  TRAINER_MANAGED
}
