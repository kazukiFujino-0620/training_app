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
public interface HealthRestingHeartRateDao {

  @Select
  Optional<HealthRestingHeartRate> selectByUserIdAndDate(Long userId, LocalDate recordDate);

  @Select
  List<HealthRestingHeartRate> selectByUserIdAndDateRange(
      Long userId, LocalDate from, LocalDate to);

  @Select
  Optional<HealthRestingHeartRate> selectLatestByUserId(Long userId);

  @Insert
  int insert(HealthRestingHeartRate entity);

  @Update
  int update(HealthRestingHeartRate entity);
}
