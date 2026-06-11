package com.example.traning.training.dao;

import com.example.traning.training.TrainingDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.seasar.doma.Column;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TrainingDetailDao {

  @Insert
  int insert(TrainingDetail trainingDetail);

  @Update
  int update(TrainingDetail trainingDetail);

  @Delete(sqlFile = true)
  int deleteByTrainingId(Long trainingId);

  @Delete(sqlFile = true)
  int deleteByUserId(Long userId);

  @Update(sqlFile = true)
  int softDeleteByTrainingId(Long trainingId);

  @Delete(sqlFile = true)
  int deleteExpiredPhysically(LocalDateTime cutoff);

  @Select
  TrainingDetail selectById(Long id);

  @Select
  List<TrainingDetail> selectByTrainingId(Long trainingId);

  @Select
  List<TrainingDetail> selectByDate(String date);

  @Select
  List<TrainingDetail> selectByUserIdAndDate(Long userId, String date);

  @Select
  Double selectTotalVolumeByUserIdAndDateRange(Long userId, LocalDate startDate, LocalDate endDate);

  @Select
  BigDecimal selectMaxWeightByUserIdAndItemAndDateRange(
      Long userId, String itemName, LocalDate startDate, LocalDate endDate);

  @Select
  List<GrowthResult> selectGrowthByItemAndPeriod(
      Long userId, String itemName, String startDate, String endDate);

  @org.seasar.doma.Entity
  public static class GrowthResult {
    @Column(name = "week_label")
    public String weekLabel;

    @Column(name = "max_weight")
    public Double maxWeight;

    @Column(name = "total_volume")
    public Double totalVolume;
  }
}
