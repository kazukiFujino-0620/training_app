package com.example.traning.periodization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** トレーナーからトレーニーへの期分けプログラムの案（2026-09-23 USER確定）。 */
@Entity
@Table(name = "program_cycle_proposals")
@Data
public class ProgramCycleProposal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trainee_user_id")
  private Long traineeUserId;

  @Column(name = "trainer_user_id")
  private Long trainerUserId;

  /** 案の元にしたプリセット。中身は案ごとの子テーブルに持つ。 */
  @Column(name = "source_preset_program_id")
  private Long sourcePresetProgramId;

  @Column(name = "name")
  private String name;

  @Column(name = "total_weeks")
  private Integer totalWeeks;

  @Column(name = "status")
  private String status;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  /** トレーナーが送信後に中身を編集した日時。 */
  @Column(name = "content_updated_at")
  private LocalDateTime contentUpdatedAt;

  @Column(name = "started_cycle_id")
  private Long startedCycleId;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
