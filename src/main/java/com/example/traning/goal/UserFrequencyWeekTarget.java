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

/** 週ごとの確定目標（スナップショット。機能見直し-1-#7 設計書5-2節）。1ユーザー×1週（月曜起点）につき1行。 */
@Entity
@Table(name = "user_frequency_week_targets")
@Data
public class UserFrequencyWeekTarget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  /** その週の月曜日。 */
  @Column(name = "week_start_date")
  private LocalDate weekStartDate;

  /** その週の目標日数。0は目標なし（評価しない）。 */
  @Column(name = "target_count")
  private Integer targetCount;

  /** {@link WeekTargetSource}の名前。 */
  @Column(name = "source")
  private String source;

  @Column(name = "program_cycle_id")
  private Long programCycleId;

  /** falseなら達成率の計算に含めない（目標を設定した週＝途中の週。QA Q7-8）。 */
  @Column(name = "evaluated")
  private Boolean evaluated;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
