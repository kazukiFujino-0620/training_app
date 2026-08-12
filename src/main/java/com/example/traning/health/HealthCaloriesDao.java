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
public interface HealthCaloriesDao {

  @Select
  Optional<HealthCalories> selectByUserIdAndDateAndSource(
      Long userId, LocalDate recordDate, String source);

  @Select
  List<HealthCalories> selectByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);

  @Insert
  int insert(HealthCalories entity);

  @Update
  int update(HealthCalories entity);
}
