package com.example.traning.smarttrainer.autogen;

import java.time.LocalDate;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

/** メニュー自動作成で参照するトレーニング記録の集計（機能見直し-1-#4）。 */
@Dao
@ConfigAutowireable
public interface AutogenHistoryDao {

  /** 種目ごとに、date以前で直近の日の完了済み本番セットのうち最も重い重量。 */
  @Select
  List<LastMainSetWeight> selectLastMainSetWeights(Long userId, LocalDate date);

  /** 期間内に完了済みの本番セットがある部位（高疲労の判定に使う）。 */
  @Select
  List<String> selectPartsWithMainSets(Long userId, LocalDate from, LocalDate to);
}
