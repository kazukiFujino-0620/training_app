package com.example.traning.smarttrainer.autogen;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 器具マスタ（機能見直し-1-#4）。 */
@Entity
@Table(name = "equipment_master")
@Data
public class Equipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name")
  private String name;

  /** FREE_WEIGHT/MACHINE/CABLE/BENCH_RACK/BODYWEIGHT */
  @Column(name = "category")
  private String category;

  /** 重量の刻み(kg)。NULLは既定値（{@link AutogenRules#DEFAULT_WEIGHT_INCREMENT_KG}）。 */
  @Column(name = "weight_increment_kg")
  private BigDecimal weightIncrementKg;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
