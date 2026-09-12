package com.example.traning.trainer;

import java.time.LocalDate;
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

  /** アドバイスの対象日（トレーナーが送信時に指定する、対象トレーニーのトレーニング実施日）。 */
  @Column(name = "target_date")
  private LocalDate targetDate;

  private String body;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  /** 既読日時。nullなら未読（ハイライト表示の対象）。本文自体は既読後も履歴として表示し続ける。 */
  @Column(name = "read_at")
  private LocalDateTime readAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}
