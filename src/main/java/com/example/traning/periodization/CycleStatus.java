package com.example.traning.periodization;

/** program_cycles.status。 */
public enum CycleStatus {
  ACTIVE,
  /** total_weeksを経過して終了した（次の選択待ち。Q3-7）。 */
  COMPLETED,
  /** 新サイクル採用により退避された。 */
  ARCHIVED
}
