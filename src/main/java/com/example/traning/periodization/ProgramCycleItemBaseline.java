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

/** サイクル採用/作成時点の種目別推定1RMスナップショット（成長グラフの計画値用、QA Q3-8）。 */
@Entity
@Table(name = "program_cycle_item_baselines")
@Data
public class ProgramCycleItemBaseline {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "cycle_id")
  private Long cycleId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "baseline_one_rm")
  private BigDecimal baselineOneRm;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
