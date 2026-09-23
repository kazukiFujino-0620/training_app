package com.example.traning.periodization;

/** 案に対するトレーニーの選択。 */
public enum ProposalResponse {
  /** 今すぐ切り替える（実施中サイクルがあれば退避し、案で開始）。 */
  START_NOW,
  /** 今のプログラムが終わったら開始（予約）。実施中サイクルがある場合のみ選べる。 */
  SCHEDULE,
  /** 断る。 */
  DECLINE
}
