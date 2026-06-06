package com.example.traning.training;

import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Table;

@Entity(immutable = false)
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "trainings")
@Table(name = "trainings")
@Data
public class Training {
  @org.seasar.doma.Id
  @jakarta.persistence.Id
  @org.seasar.doma.GeneratedValue(strategy = org.seasar.doma.GenerationType.IDENTITY)
  @jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
  private Long id;

  @Column(name = "is_all_completed")
  private boolean isAllCompleted;

  @Column(name = "is_completed")
  private Boolean isCompleted = false;

  @Column(name = "user_id")
  @NotNull(message = "ユーザーIDは必須です")
  private Long userId;

  @Column(name = "training_date")
  @NotNull(message = "トレーニング日は必須です")
  private LocalDate trainingDate;

  @Column(name = "part_code")
  @NotBlank(message = "部位コードは必須です")
  private String partCode;

  @Column(name = "superset_group_id")
  private Long supersetGroupId;

  @Column(name = "menu")
  @NotBlank(message = "メニューは必須です")
  private String menu;

  @Column(name = "memo")
  @Size(max = 500, message = "メモは500文字以内で入力してください")
  private String memo;

  @Column(name = "duration")
  private String duration;

  @Column(name = "create_datetime")
  private LocalDateTime createDatetime = LocalDateTime.now();

  @Column(name = "updated_datetime")
  private LocalDateTime updatedDatetime = LocalDateTime.now();

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @org.seasar.doma.Transient
  @OneToMany(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
  @NotNull(message = "セットデータは必須です")
  @Valid
  private List<TrainingDetail> details = new ArrayList<>();

  @org.seasar.doma.Transient @jakarta.persistence.Transient private String partName;

  // 手動セッターメソッド（Lombokの問題回避）
  public void setIsAllCompleted(boolean isAllCompleted) {
    this.isAllCompleted = isAllCompleted;
  }

  public boolean isAllCompleted() {
    return this.isAllCompleted;
  }
}
