package com.example.traning.periodization;

/** program_cycle_proposals.status。 */
public enum ProposalStatus {
  /** トレーニーの選択待ち。 */
  PENDING("返事待ち"),
  /** 「今のプログラムが終わったら開始」を選んだ（予約）。実施中サイクルの終了時に自動で開始する。 */
  SCHEDULED("予約"),
  /** サイクルとして開始済み（即時・予約の自動開始とも）。 */
  STARTED("開始済み"),
  /** トレーニーが断った。 */
  DECLINED("断られました"),
  /** 返事待ちのうちに同じトレーニーへ新しい案が送られ、置き換えられた（2026-09-24 USER確定）。 */
  SUPERSEDED("新しい案に置き換え"),
  /** 返事待ちのうちにトレーナーが取り下げた（2026-09-26 USER確定。予約中は取り下げ不可）。 */
  WITHDRAWN("取り下げ"),
  /** 予約中にトレーニーが自分で別のプログラムに切り替えたため取り消し（2026-09-26 USER確定B）。 */
  CANCELLED_BY_SWITCH("本人が別のプログラムに切り替えたため取り消し");

  private final String label;

  ProposalStatus(String label) {
    this.label = label;
  }

  /** 画面表示用の状態名（トレーナー側「これまでに送った案」等）。 */
  public String label() {
    return label;
  }
}
