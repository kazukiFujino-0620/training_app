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

  @Update(sqlFile = true)
  int softDeleteById(Long id);

  @Update(sqlFile = true)
  int restoreByTrainingId(Long trainingId);

  @Delete(sqlFile = true)
  int deleteExpiredPhysically(LocalDateTime cutoff);

  @Select
  TrainingDetail selectById(Long id);

  @Select
  List<TrainingDetail> selectByTrainingId(Long trainingId);

  @Select
  List<TrainingDetail> selectByDate(String date);

  /**
   * 組織スコープで絞り込んだ日付別トレーニング詳細一覧（ita1-1 フェーズ3、管理者用）。
   *
   * @param organizationIds {@code null} の場合は絞り込みなし（ROLE_ADMIN）。空リストは呼び出し側で0件として扱うこと（IN
   *     句が空になるため呼び出し禁止）。
   */
  @Select
  List<TrainingDetail> selectByDateAndOrganizationIds(String date, List<Long> organizationIds);

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

  /** 部位別の総ボリューム（重量×回数の合計）を集計する（ita4-1 週次・月次サマリー通知用）。 */
  @Select
  List<PartVolume> selectVolumeByPartAndDateRange(
      Long userId, LocalDate startDate, LocalDate endDate);

  @org.seasar.doma.Entity
  public static class GrowthResult {
    @Column(name = "week_label")
    public String weekLabel;

    @Column(name = "max_weight")
    public Double maxWeight;

    @Column(name = "total_volume")
    public Double totalVolume;
  }

  @org.seasar.doma.Entity
  public static class PartVolume {
    @Column(name = "part_code")
    public String partCode;

    @Column(name = "total_volume")
    public Double totalVolume;
  }
}
