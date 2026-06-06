package com.example.traning.template;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.Transient;

@Entity
@Table(name = "training_templates")
@Data
public class TrainingTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  private String name;

  @Column(name = "part_code")
  private String partCode;

  private String memo;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Transient private List<TrainingTemplateItem> items;
}
