package com.example.traning.body;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface BodyMeasurementDao {

  @Select
  List<BodyMeasurement> selectByUserId(Long userId);

  @Select
  List<BodyMeasurement> selectByUserIdAndDateRange(Long userId, LocalDate from, LocalDate to);

  @Select
  Optional<BodyMeasurement> selectByUserIdAndDate(Long userId, LocalDate date);

  @Insert
  int insert(BodyMeasurement m);

  @Update
  int update(BodyMeasurement m);

  @Delete(sqlFile = true)
  int deleteById(Long id, Long userId);
}
