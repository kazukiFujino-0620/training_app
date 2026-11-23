package com.example.traning.goal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** トレーナーからの目標の提案（機能見直し-1-#20）。会員が承認すると週の回数目標と目的モードが確定する。 */
@Entity
@Table(name = "goal_proposals")
@Data
public class GoalProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_user_id")
  private Long memberUserId;

  @Column(name = "proposer_user_id")
  private Long proposerUserId;

  @Column(name = "weekly_target_count")
  private Integer weeklyTargetCount;

  /** {@link com.example.traning.smarttrainer.recommendation.GoalMode}の名前。 */
  @Column(name = "goal_mode")
  private String goalMode;

  @Column(name = "comment")
  private String comment;

  @Column(name = "next_review_date")
  private LocalDate nextReviewDate;

  /** {@link GoalProposalStatus}の名前。 */
  @Column(name = "status")
  private String status;

  @Column(name = "proposed_at")
  private LocalDateTime proposedAt;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  /** 提案した人の名前（表示用。usersと結合して取得し、保存はしない）。 */
  @Column(name = "proposer_name", insertable = false, updatable = false)
  private String proposerName;
}
