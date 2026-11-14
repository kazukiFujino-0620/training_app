package com.example.traning.smarttrainer.autogen;

import java.time.LocalDate;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;

/** 種目ごとの直近のセッションの完了済み本番セット（推定1RMの計算に使う）。検索結果の受け皿で、テーブルは持たない。 */
@Entity
@Data
public class RecentMainSet {

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "training_date")
  private LocalDate trainingDate;

  @Column(name = "weight")
  private Double weight;

  @Column(name = "reps")
  private Integer reps;
}
