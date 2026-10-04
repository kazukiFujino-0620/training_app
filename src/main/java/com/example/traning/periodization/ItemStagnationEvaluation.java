package com.example.traning.periodization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 種目別の停滞判定結果（記録保存時に判定して保持。QA Q3-4 2026-09-24追記）。 */
@Entity
@Table(name = "item_stagnation_evaluations")
@Data
public class ItemStagnationEvaluation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "level")
  private String level;

  @Column(name = "evaluated_at")
  private LocalDateTime evaluatedAt;
}
