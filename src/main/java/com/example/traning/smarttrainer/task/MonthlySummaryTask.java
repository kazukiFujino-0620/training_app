package com.example.traning.smarttrainer.task;

import com.example.traning.common.SummaryMailService;
import com.example.traning.common.SummaryMailService.GoalAchievementResult;
import com.example.traning.dao.UserDao;
import com.example.traning.goal.GoalDao;
import com.example.traning.goal.TrainingGoal;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySummaryTask {

  private final UserDao userDao;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final GoalDao goalDao;
  private final SummaryMailService summaryMailService;

  @Scheduled(cron = "${batch.summary.monthly.cron}")
  public void sendMonthlySummary() {
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    LocalDate monthStart = lastMonth.atDay(1);
    LocalDate monthEnd = lastMonth.atEndOfMonth();

    YearMonth prevMonth = lastMonth.minusMonths(1);
    LocalDate prevStart = prevMonth.atDay(1);
    LocalDate prevEnd = prevMonth.atEndOfMonth();

    log.info("月次サマリーバッチ開始 - 対象月: {}", lastMonth);

    List<User> users = userDao.selectAll();
    int success = 0;
    int skip = 0;

    for (User user : users) {
      try {
        Long userId = user.getUserId().longValue();

        int sessionCount = trainingDao.countByUserIdAndDateRange(userId, monthStart, monthEnd);
        Double volume =
            trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, monthStart, monthEnd);
        Double prevVolume =
            trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, prevStart, prevEnd);
        Double changePercent = calcChangePercent(volume, prevVolume);

        List<TrainingDao.PartSessionCount> partCounts =
            trainingDao.countSessionsByPartAndDateRange(userId, monthStart, monthEnd);
        Map<String, Integer> partMap =
            partCounts.stream().collect(Collectors.toMap(p -> p.partCode, p -> p.sessionCount));

        List<TrainingGoal> goals = goalDao.selectByUserId(userId);
        List<GoalAchievementResult> goalResults =
            goals.stream().map(g -> checkGoalAchievement(g, userId, monthStart, monthEnd)).toList();

        summaryMailService.sendMonthlySummary(
            user.email,
            user.userName,
            lastMonth.getYear(),
            lastMonth.getMonthValue(),
            sessionCount,
            volume != null ? volume : 0.0,
            partMap,
            changePercent,
            goalResults);
        success++;
      } catch (Exception e) {
        log.warn("月次サマリー送信スキップ - userId={}, reason={}", user.getUserId(), e.getMessage());
        skip++;
      }
    }

    log.info("月次サマリーバッチ完了 - 成功: {}, スキップ: {}", success, skip);
  }

  private GoalAchievementResult checkGoalAchievement(
      TrainingGoal goal, Long userId, LocalDate start, LocalDate end) {

    if ("ACHIEVED".equals(goal.getStatus())) {
      return new GoalAchievementResult(goal.getItemName(), goal.getTargetWeight(), null, true);
    }

    BigDecimal maxWeight =
        trainingDetailDao.selectMaxWeightByUserIdAndItemAndDateRange(
            userId, goal.getItemName(), start, end);

    boolean achieved = maxWeight != null && maxWeight.compareTo(goal.getTargetWeight()) >= 0;

    return new GoalAchievementResult(
        goal.getItemName(), goal.getTargetWeight(), maxWeight, achieved);
  }

  private Double calcChangePercent(Double current, Double prev) {
    if (prev == null || prev == 0.0) return null;
    double cur = current != null ? current : 0.0;
    return (cur - prev) / prev * 100.0;
  }
}
