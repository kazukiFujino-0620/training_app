package com.example.traning.periodization;

import java.math.BigDecimal;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** プリセットの週別目標強度・ディロード週フラグ。 */
@Entity
@Table(name = "preset_program_weeks")
@Data
public class PresetProgramWeek {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "preset_program_id")
  private Long presetProgramId;

  @Column(name = "week_number")
  private Integer weekNumber;

  @Column(name = "target_intensity_pct")
  private BigDecimal targetIntensityPct;

  @Column(name = "is_deload")
  private Boolean deload;
}
