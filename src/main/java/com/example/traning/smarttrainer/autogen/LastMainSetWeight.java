package com.example.traning.smarttrainer.autogen;

import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;

/** 種目ごとの直近の本番セットの重量（推定1RMが無い種目の重量案に使う）。検索結果の受け皿で、テーブルは持たない。 */
@Entity
@Data
public class LastMainSetWeight {

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "weight")
  private Double weight;
}
