package com.example.traning.periodization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** サイクル内・週別の曜日→部位割当（weekly_programsの期分け版）。 */
@Entity
@Table(name = "program_cycle_day_templates")
@Data
public class ProgramCycleDayTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cycle_id")
  private Long cycleId;

  @Column(name = "week_number")
  private Integer weekNumber;

  @Column(name = "day_of_week")
  private String dayOfWeek;

  @Column(name = "part_code")
  private String partCode;

  @Column(name = "template_id")
  private Long templateId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private LocalDateTime updatedAt;
}
