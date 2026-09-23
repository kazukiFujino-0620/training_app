package com.example.traning.periodization;

import java.time.LocalDateTime;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

/** 種目別の停滞判定結果のDAO。 */
@Dao
@ConfigAutowireable
public interface ItemStagnationEvaluationDao {

  @Select
  List<ItemStagnationEvaluation> selectByUserIdAndItemNames(Long userId, List<String> itemNames);

  /** 1ユーザー×1種目の判定結果を登録または上書きする。 */
  @Insert(sqlFile = true)
  int upsert(Long userId, String itemName, String level, LocalDateTime evaluatedAt);
}
