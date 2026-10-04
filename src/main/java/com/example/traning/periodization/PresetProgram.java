package com.example.traning.periodization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 既定プログラム（プリセット）マスタ。運営側が用意する読み取り専用データ（機能見直し-1-#3）。 */
@Entity
@Table(name = "preset_programs")
@Data
public class PresetProgram {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "name")
  private String name;

  @Column(name = "purpose_category")
  private String purposeCategory;

  @Column(name = "total_weeks")
  private Integer totalWeeks;

  @Column(name = "description")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
