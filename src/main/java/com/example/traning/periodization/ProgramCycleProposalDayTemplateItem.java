package com.example.traning.periodization;

import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 案の曜日別種目リスト。 */
@Entity
@Table(name = "program_cycle_proposal_day_template_items")
@Data
public class ProgramCycleProposalDayTemplateItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "day_template_id")
  private Long dayTemplateId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "target_sets")
  private Integer targetSets;
}
