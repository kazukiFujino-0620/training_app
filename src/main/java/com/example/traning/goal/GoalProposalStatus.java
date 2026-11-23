package com.example.traning.goal;

/** goal_proposals.status（機能見直し-1-#20）。 */
public enum GoalProposalStatus {
  PENDING("返事待ち"),
  ACCEPTED("承認"),
  DECLINED("見送り"),
  /** 返事待ちのうちに新しい提案が送られた。 */
  WITHDRAWN("取り下げ");

  private final String label;

  GoalProposalStatus(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
