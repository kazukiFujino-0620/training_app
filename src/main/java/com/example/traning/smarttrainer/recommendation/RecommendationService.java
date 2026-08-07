package com.example.traning.smarttrainer.recommendation;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.dao.PersonalRecordDao;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** F3 Phase 1: 推奨エンジンのコーディネーション役。 各種DAOからデータを集め、{@link RecommendationEngine} に渡す。 */
@Service
public class RecommendationService {

  /** 種目選定に使う実施頻度集計の対象期間（日数） */
  private static final int RECENT_ITEM_WINDOW_DAYS = 30;

  /** 「最終実施日」判定に使う遡り期間（日数）。これより前は「未実施」として扱う。 */
  private static final int LAST_TRAINED_LOOKBACK_DAYS = 90;

  private final TrainingDao trainingDao;
  private final TrainingMasterDao trainingMasterDao;
  private final PersonalRecordDao personalRecordDao;
  private final UserDao userDao;
  private final FatigueCalculator fatigueCalculator;
  private final RecommendationEngine recommendationEngine;
  private final Map<GoalMode, RecommendationStrategy> strategies;

  public RecommendationService(
      TrainingDao trainingDao,
      TrainingMasterDao trainingMasterDao,
      PersonalRecordDao personalRecordDao,
      UserDao userDao,
      FatigueCalculator fatigueCalculator,
      RecommendationEngine recommendationEngine,
      List<RecommendationStrategy> strategyList) {
    this.trainingDao = trainingDao;
    this.trainingMasterDao = trainingMasterDao;
    this.personalRecordDao = personalRecordDao;
    this.userDao = userDao;
    this.fatigueCalculator = fatigueCalculator;
    this.recommendationEngine = recommendationEngine;
    this.strategies = new EnumMap<>(GoalMode.class);
    for (RecommendationStrategy s : strategyList) {
      this.strategies.put(s.getMode(), s);
    }
  }

  public DailyRecommendation getTodayRecommendation(Long userId) {
    LocalDate today = LocalDate.now();
    User user = userDao.selectById(userId.intValue());
    GoalMode mode = GoalMode.fromString(user.getCurrentGoalMode());
    RecommendationStrategy strategy = strategies.get(mode);

    boolean isNewUser =
        trainingDao.countByUserIdAndDateRange(userId, LocalDate.of(2000, 1, 1), today) == 0;

    FatigueCalculator.FatigueResult fatigueResult = fatigueCalculator.calculate(userId, today);

    LocalDate lookbackStart = today.minusDays(LAST_TRAINED_LOOKBACK_DAYS);
    List<Training> lookbackTrainings =
        trainingDao.selectByUserIdAndDateRange(userId.intValue(), lookbackStart, today);

    Map<String, LocalDate> lastTrained = new LinkedHashMap<>();
    Map<String, Map<String, Long>> itemCountByPart = new LinkedHashMap<>();
    LocalDate recentWindowStart = today.minusDays(RECENT_ITEM_WINDOW_DAYS);
    for (Training t : lookbackTrainings) {
      String pc = t.getPartCode();
      if (pc == null) continue;
      lastTrained.merge(pc, t.getTrainingDate(), (a, b) -> a.isAfter(b) ? a : b);
      if (!t.getTrainingDate().isBefore(recentWindowStart)) {
        itemCountByPart
            .computeIfAbsent(pc, k -> new LinkedHashMap<>())
            .merge(t.getMenu(), 1L, Long::sum);
      }
    }

    Map<String, List<String>> recentItemNamesByPart = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Long>> e : itemCountByPart.entrySet()) {
      List<String> top3 =
          e.getValue().entrySet().stream()
              .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
              .map(Map.Entry::getKey)
              .limit(3)
              .toList();
      recentItemNamesByPart.put(e.getKey(), top3);
    }

    Map<String, List<TrainingItemMaster>> masterItemsByPart = new LinkedHashMap<>();
    for (String p : FatigueCalculator.PART_ORDER) {
      masterItemsByPart.put(p, trainingMasterDao.selectItemsByPart(p));
    }

    List<PersonalRecord> userPrs = personalRecordDao.selectByUserId(userId);

    return recommendationEngine.generate(
        today,
        isNewUser,
        fatigueResult.fatiguePct(),
        lastTrained,
        userPrs,
        recentItemNamesByPart,
        masterItemsByPart,
        strategy,
        user.getWeightKg());
  }
}
