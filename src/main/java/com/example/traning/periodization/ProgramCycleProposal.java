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

  @Column(name = "preset_program_id")
  private Long presetProgramId;

  @Column(name = "status")
  private String status;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  @Column(name = "started_cycle_id")
  private Long startedCycleId;

  @Column(name = "started_at")
  private LocalDateTime startedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
