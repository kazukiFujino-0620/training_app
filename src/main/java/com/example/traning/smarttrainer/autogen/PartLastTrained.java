package com.example.traning.smarttrainer.autogen;

import java.time.LocalDate;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;

/** 部位ごとの最後にやった日（おまかせの部位の優先度に使う）。検索結果の受け皿で、テーブルは持たない。 */
@Entity
@Data
public class PartLastTrained {

  @Column(name = "part_code")
  private String partCode;

  @Column(name = "last_date")
  private LocalDate lastDate;
}
