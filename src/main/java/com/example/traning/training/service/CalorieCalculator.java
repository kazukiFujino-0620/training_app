package com.example.traning.training.service;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.training.SetType;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 消費カロリー推定（ita2-2、力学的仕事量ベース＝案Bで確定）。
 *
 * <p>仕事量(J) = 挙上重量(kg) × 重力加速度(9.8) × 可動域ROM(m) × 回数<br>
 * 消費カロリー(kcal) = 仕事量(J) ÷ 筋効率(約20%) ÷ 4184(J→kcal換算)
 *
 * <p>体組成・トレーニング時間には依存しない（従来のMET×体重×時間方式から置き換え）。対象は筋トレ種目のみ（有酸素はita2-1で別管理）。
 */
@Component
public class CalorieCalculator {

  private static final double GRAVITY = 9.8;
  private static final double MUSCLE_EFFICIENCY = 0.20;
  private static final double JOULES_PER_KCAL = 4184.0;

  public enum CalorieType {
    /** 計算成功。 */
    CALCULATED,
    /** 対象セットが無い、または種目マスタにROMが整備されていない種目のみで計算不可。 */
    UNAVAILABLE
  }

  public static class CalorieEstimate {
    public final CalorieType type;
    public final Integer calories;

    public CalorieEstimate(CalorieType type, Integer calories) {
      this.type = type;
      this.calories = calories;
    }
  }

  /**
   * @param trainings 対象セッションのトレーニング一覧（各Trainingにdetailsが設定済みであること）
   * @param itemMasterByName 種目名 → 種目マスタ（ROM取得用）
   */
  public CalorieEstimate estimate(
      List<Training> trainings, Map<String, TrainingItemMaster> itemMasterByName) {
    if (trainings == null || trainings.isEmpty() || itemMasterByName == null) {
      return new CalorieEstimate(CalorieType.UNAVAILABLE, null);
    }

    double totalJoules = 0;
    boolean anyMatched = false;

    for (Training training : trainings) {
      // 有酸素運動（ita2-1）は本計算式の対象外。消費カロリーは手入力のTrainingDetail.caloriesKcalで別管理する。
      if ("CARDIO".equals(training.getPartCode())) {
        continue;
      }
      TrainingItemMaster item = itemMasterByName.get(training.getMenu());
      if (item == null || item.getRangeOfMotionM() == null) {
        continue;
      }
      double rom = item.getRangeOfMotionM().doubleValue();

      List<TrainingDetail> details = training.getDetails();
      if (details == null) {
        continue;
      }
      for (TrainingDetail detail : details) {
        // WARMUP/DROPはPR・ボリューム集計と同様に対象から除外する
        if (SetType.fromValueOrMain(detail.getSetType()).isVolumeExcluded()) {
          continue;
        }
        if (detail.getWeight() == null || detail.getReps() == null) {
          continue;
        }
        anyMatched = true;
        totalJoules += detail.getWeight() * GRAVITY * rom * detail.getReps();
      }
    }

    if (!anyMatched) {
      return new CalorieEstimate(CalorieType.UNAVAILABLE, null);
    }

    double kcal = totalJoules / MUSCLE_EFFICIENCY / JOULES_PER_KCAL;
    return new CalorieEstimate(CalorieType.CALCULATED, (int) Math.round(kcal));
  }
}
