package com.example.traning.template;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "training_template_items")
@Data
public class TrainingTemplateItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "template_id")
  private Long templateId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "set_number")
  private Integer setNumber;

  @Column(name = "set_type")
  private String setType = "MAIN";

  private BigDecimal weight;

  private Integer reps;

  @Column(name = "display_order")
  private Integer displayOrder = 0;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();
}
