package com.example.traning.smarttrainer.prediction;

import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 離脱予測（簡易版）: 直近の記録頻度が落ちているユーザーを検知する。
 * ML不使用のシンプルなルール（最終トレーニング日からの経過日数のみ）。
 * [[2026-06-06-ai-menu-requirements]] Phase 2参照。
 */
@Service
public class ChurnDetectionService {

  public static final int CHURN_THRESHOLD_DAYS = 3;
  private static final int LOOKBACK_DAYS = 30;

  private final TrainingDao trainingDao;

  public ChurnDetectionService(TrainingDao trainingDao) {
    this.trainingDao = trainingDao;
  }

  /** @return 3日以上記録がなければ「久しぶりですね」メッセージ、なければ empty */
  public Optional<String> checkChurnMessage(Long userId, LocalDate today) {
    LocalDate lookbackStart = today.minusDays(LOOKBACK_DAYS);
    List<Training> recent =
        trainingDao.selectByUserIdAndDateRange(userId.intValue(), lookbackStart, today);

    Optional<LocalDate> lastDate =
        recent.stream().map(Training::getTrainingDate).max(Comparator.naturalOrder());

    long daysSince =
        lastDate.map(d -> ChronoUnit.DAYS.between(d, today)).orElse((long) LOOKBACK_DAYS + 1);

    if (daysSince >= CHURN_THRESHOLD_DAYS) {
      return Optional.of("久しぶりですね。" + daysSince + "日ぶりのトレーニング、無理せず始めましょう。");
    }
    return Optional.empty();
  }
}
