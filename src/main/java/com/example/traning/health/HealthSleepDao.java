package com.example.traning.health;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface HealthSleepDao {

  /** 機能見直し-1-#5でUNIQUE制約を(user_id, sleep_date)に変更したため、1日1行として取得する。 */
  @Select
  Optional<HealthSleep> selectByUserIdAndDate(Long userId, LocalDate sleepDate);

  @Select
  List<HealthSleep> selectByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);

  @Select
  Optional<HealthSleep> selectLatestByUserId(Long userId);

  @Insert
  int insert(HealthSleep entity);

  @Update
  int update(HealthSleep entity);
}
