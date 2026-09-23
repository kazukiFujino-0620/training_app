package com.example.traning.periodization;

/** program_cycle_proposals.status。 */
public enum ProposalStatus {
  /** トレーニーの選択待ち。 */
  PENDING,
  /** 「今のプログラムが終わったら開始」を選んだ（予約）。実施中サイクルの終了時に自動で開始する。 */
  SCHEDULED,
  /** サイクルとして開始済み（即時・予約の自動開始とも）。 */
  STARTED,
  /** トレーニーが断った。 */
  DECLINED
}
