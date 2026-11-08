package com.example.traning.smarttrainer.autogen;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 種目ごとの必要器具（機能見直し-1-#4）。 */
@Entity
@Table(name = "training_item_equipment")
@Data
public class TrainingItemEquipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "equipment_id")
  private Long equipmentId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
