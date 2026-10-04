package com.example.traning.periodization;

import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** プリセットの週別・曜日→部位割当。 */
@Entity
@Table(name = "preset_program_day_templates")
@Data
public class PresetProgramDayTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "preset_program_id")
  private Long presetProgramId;

  @Column(name = "week_number")
  private Integer weekNumber;

  @Column(name = "day_of_week")
  private String dayOfWeek;

  @Column(name = "part_code")
  private String partCode;
}
