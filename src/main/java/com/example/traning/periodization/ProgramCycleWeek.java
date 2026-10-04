package com.example.traning.periodization;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** サイクル内の週別目標強度・ディロード週フラグ。 */
@Entity
@Table(name = "program_cycle_weeks")
@Data
public class ProgramCycleWeek {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cycle_id")
  private Long cycleId;

  @Column(name = "week_number")
  private Integer weekNumber;

  @Column(name = "target_intensity_pct")
  private BigDecimal targetIntensityPct;

  @Column(name = "is_deload")
  private Boolean deload;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
