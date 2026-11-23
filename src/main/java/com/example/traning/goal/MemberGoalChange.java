package com.example.traning.goal;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 会員が自分で週の回数目標・目的モードを変えた記録（機能見直し-1-#20 Q20-1）。 */
@Entity
@Table(name = "member_goal_changes")
@Data
public class MemberGoalChange {

  /** 週の回数目標。 */
  public static final String WEEKLY_TARGET = "WEEKLY_TARGET";

  /** 目的モード。 */
  public static final String GOAL_MODE = "GOAL_MODE";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "member_user_id")
  private Long memberUserId;

  /** {@link #WEEKLY_TARGET} または {@link #GOAL_MODE}。 */
  @Column(name = "change_type")
  private String changeType;

  @Column(name = "old_value")
  private String oldValue;

  @Column(name = "new_value")
  private String newValue;

  @Column(name = "changed_at")
  private LocalDateTime changedAt;
}
