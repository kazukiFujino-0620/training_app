package com.example.traning.trainer;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** トレーナーアドバイス（ita4-4 (A)）。特定のトレーニング記録には紐付かない時系列・自由記述のメッセージ。 */
@Entity
@Table(name = "trainer_advices")
@Data
public class TrainerAdvice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "trainer_id")
  private Long trainerId;

  @Column(name = "target_user_id")
  private Long targetUserId;

  private String body;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}
