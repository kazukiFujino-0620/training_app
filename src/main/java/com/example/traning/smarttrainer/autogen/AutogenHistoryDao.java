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

  /**
   * 種目ごとに、date以前で直近のセッション（日付）sessions件分の完了済み本番セット（重量0より大きいもの）。 推定1RM・休止補正・上限キャップの元（QA
   * Q4-12）。生涯ベスト（personal_records）は使わない。
   */
  @Select
  List<RecentMainSet> selectRecentMainSets(Long userId, LocalDate date, int sessions);

  /** 部位ごとに、date以前で完了済みのセットがある最後の日（疲労計算と同じ記録。QA Q4-13のおまかせの優先度）。 */
  @Select
  List<PartLastTrained> selectLastTrainedDateByPart(Long userId, LocalDate date);

  /** 期間内に完了済みの本番セットがある部位（高疲労の判定に使う）。 */
  @Select
  List<String> selectPartsWithMainSets(Long userId, LocalDate from, LocalDate to);
}
