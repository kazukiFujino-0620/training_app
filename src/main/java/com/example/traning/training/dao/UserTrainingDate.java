package com.example.traning.training.dao;

import java.time.LocalDate;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;

/** 会員ごとのトレーニング日（機能見直し-1-#12 担当会員の一覧の一括取得）。検索結果の受け皿で、テーブルは持たない。 */
@Entity
@Data
public class UserTrainingDate {

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "training_date")
  private LocalDate trainingDate;
}
