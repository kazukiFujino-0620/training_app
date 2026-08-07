package com.example.traning.smarttrainer.prediction;

import com.example.traning.training.dao.TrainingDetailDao;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * ACWR（Acute:Chronic Workload Ratio）計算。 直近1週間の総負荷 ÷ 過去4週間の週平均負荷。スポーツ科学の公知指標（Gabbett, 2016
 * BJSM）で特許リスクなし。 [[2026-06-06-ai-menu-requirements]] Phase 2参照。
 */
@Service
public class AcwrService {

  public static final double WARNING_THRESHOLD = 1.5;

  private final TrainingDetailDao trainingDetailDao;

  public AcwrService(TrainingDetailDao trainingDetailDao) {
    this.trainingDetailDao = trainingDetailDao;
  }

  /**
   * @return ACWR値。慢性負荷（過去4週間）が0（データ不足）の場合は null（判定不可）。
   */
  public Double calculate(Long userId, LocalDate today) {
    LocalDate acuteStart = today.minusDays(6);
    Double acuteVolume =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, acuteStart, today);

    LocalDate chronicStart = today.minusDays(27);
    Double chronicTotal =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, chronicStart, today);

    double acute = acuteVolume != null ? acuteVolume : 0.0;
    double chronicWeekAvg = (chronicTotal != null ? chronicTotal : 0.0) / 4.0;

    if (chronicWeekAvg <= 0) return null;
    return acute / chronicWeekAvg;
  }

  public boolean isWarning(Double acwr) {
    return acwr != null && acwr > WARNING_THRESHOLD;
  }
}
