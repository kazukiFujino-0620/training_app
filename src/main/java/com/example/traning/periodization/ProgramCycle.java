package com.example.traning.periodization;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** ユーザーが採用している期分けサイクル。 */
@Entity
@Table(name = "program_cycles")
@Data
public class ProgramCycle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "name")
  private String name;

  @Column(name = "total_weeks")
  private Integer totalWeeks;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "tier")
  private String tier;

  @Column(name = "source_preset_id")
  private Long sourcePresetId;

  @Column(name = "created_by_trainer_id")
  private Long createdByTrainerId;

  @Column(name = "status")
  private String status;

  /** サイクル終了後の次の行き先（renewCycleの3択）を選んだ日時。NULLかつCOMPLETEDなら選択待ち（QA Q3-7追記）。 */
  @Column(name = "renew_decided_at")
  private LocalDateTime renewDecidedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
