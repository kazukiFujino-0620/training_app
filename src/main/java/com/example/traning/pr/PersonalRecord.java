package com.example.traning.pr;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "personal_records")
@Data
public class PersonalRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "max_weight")
  private Double maxWeight;

  @Column(name = "max_reps")
  private Integer maxReps;

  @Column(name = "achieved_date")
  private LocalDate achievedDate;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();
}
