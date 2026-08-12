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
public interface HealthHeartRateDao {

  @Select
  Optional<HealthHeartRate> selectByUserIdAndDateAndSource(
      Long userId, LocalDate recordDate, String source);

  @Select
  List<HealthHeartRate> selectByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);

  @Select
  Optional<HealthHeartRate> selectLatestByUserId(Long userId);

  @Insert
  int insert(HealthHeartRate entity);

  @Update
  int update(HealthHeartRate entity);
}
