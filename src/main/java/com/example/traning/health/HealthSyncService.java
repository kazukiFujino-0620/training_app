package com.example.traning.health;

import com.example.traning.body.BodyMeasurement;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.mobile.dto.HealthSummaryResponse;
import com.example.traning.mobile.dto.HealthSyncRequest;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HealthKit/Health Connectから同期された健康データの取り込み処理（ita3-1、読み取り専用連携）。 */
@Service
@RequiredArgsConstructor
public class HealthSyncService {

  private final BodyMeasurementService bodyMeasurementService;
  private final HealthStepsDao healthStepsDao;
  private final HealthHeartRateDao healthHeartRateDao;
  private final HealthCaloriesDao healthCaloriesDao;
  private final HealthSleepService healthSleepService;
  private final HealthRestingHeartRateService healthRestingHeartRateService;

  @Transactional
  public int sync(Long userId, HealthSyncRequest req) {
    String source = req.getSource();
    int count = 0;

    if (req.getWeight() != null) {
      for (HealthSyncRequest.WeightRecord r : req.getWeight()) {
        bodyMeasurementService.syncFromHealthPlatform(
            userId, r.getDate(), r.getWeightKg(), r.getBodyFatPct(), source);
        count++;
      }
    }

    if (req.getSteps() != null) {
      for (HealthSyncRequest.StepsRecord r : req.getSteps()) {
        syncSteps(userId, r, source);
        count++;
      }
    }

    if (req.getHeartRate() != null) {
      for (HealthSyncRequest.HeartRateRecord r : req.getHeartRate()) {
        syncHeartRate(userId, r, source);
        count++;
      }
    }

    if (req.getCalories() != null) {
      for (HealthSyncRequest.CaloriesRecord r : req.getCalories()) {
        syncCalories(userId, r, source);
        count++;
      }
    }

    if (req.getSleep() != null) {
      for (HealthSyncRequest.SleepRecord r : req.getSleep()) {
        healthSleepService.syncFromHealthPlatform(
            userId, r.getDate(), r.getStartTime(), r.getEndTime(), r.getDurationMinutes(), source);
        count++;
      }
    }

    // 機能見直し-1-#5: 安静時心拍（health_heart_rateとは別テーブル、1日1行の上書き）
    if (req.getRestingHeartRate() != null) {
      for (HealthSyncRequest.RestingHeartRateRecord r : req.getRestingHeartRate()) {
        healthRestingHeartRateService.syncFromHealthPlatform(
            userId, r.getDate(), r.getRestingBpm(), source);
        count++;
      }
    }

    return count;
  }

  private void syncSteps(Long userId, HealthSyncRequest.StepsRecord r, String source) {
    Optional<HealthSteps> existing =
        healthStepsDao.selectByUserIdAndDateAndSource(userId, r.getDate(), source);
    if (existing.isPresent()) {
      HealthSteps e = existing.get();
      e.stepCount = r.getStepCount();
      healthStepsDao.update(e);
    } else {
      HealthSteps e = new HealthSteps();
      e.userId = userId;
      e.recordDate = r.getDate();
      e.stepCount = r.getStepCount();
      e.source = source;
      healthStepsDao.insert(e);
    }
  }

  private void syncHeartRate(Long userId, HealthSyncRequest.HeartRateRecord r, String source) {
    Optional<HealthHeartRate> existing =
        healthHeartRateDao.selectByUserIdAndDateAndSource(userId, r.getDate(), source);
    if (existing.isPresent()) {
      HealthHeartRate e = existing.get();
      e.avgBpm = r.getAvgBpm();
      e.minBpm = r.getMinBpm();
      e.maxBpm = r.getMaxBpm();
      healthHeartRateDao.update(e);
    } else {
      HealthHeartRate e = new HealthHeartRate();
      e.userId = userId;
      e.recordDate = r.getDate();
      e.avgBpm = r.getAvgBpm();
      e.minBpm = r.getMinBpm();
      e.maxBpm = r.getMaxBpm();
      e.source = source;
      healthHeartRateDao.insert(e);
    }
  }

  private void syncCalories(Long userId, HealthSyncRequest.CaloriesRecord r, String source) {
    Optional<HealthCalories> existing =
        healthCaloriesDao.selectByUserIdAndDateAndSource(userId, r.getDate(), source);
    if (existing.isPresent()) {
      HealthCalories e = existing.get();
      e.activeCalories = r.getActiveCalories();
      e.totalCalories = r.getTotalCalories();
      healthCaloriesDao.update(e);
    } else {
      HealthCalories e = new HealthCalories();
      e.userId = userId;
      e.recordDate = r.getDate();
      e.activeCalories = r.getActiveCalories();
      e.totalCalories = r.getTotalCalories();
      e.source = source;
      healthCaloriesDao.insert(e);
    }
  }

  /** 各指標の最新値をまとめて返す（表示画面用）。データが無い項目はnullのまま返す。 */
  @Transactional(readOnly = true)
  public HealthSummaryResponse getSummary(Long userId) {
    HealthSummaryResponse response = new HealthSummaryResponse();

    bodyMeasurementService
        .getLatest(userId)
        .ifPresent(
            (BodyMeasurement m) ->
                response.setWeight(
                    new HealthSummaryResponse.WeightSummary(
                        m.measuredDate, m.weightKg, m.bodyFatPct, m.source)));

    healthStepsDao
        .selectLatestByUserId(userId)
        .ifPresent(
            s ->
                response.setSteps(
                    new HealthSummaryResponse.StepsSummary(s.recordDate, s.stepCount, s.source)));

    healthHeartRateDao
        .selectLatestByUserId(userId)
        .ifPresent(
            h ->
                response.setHeartRate(
                    new HealthSummaryResponse.HeartRateSummary(
                        h.recordDate, h.avgBpm, h.minBpm, h.maxBpm, h.source)));

    healthCaloriesDao
        .selectLatestByUserId(userId)
        .ifPresent(
            c ->
                response.setCalories(
                    new HealthSummaryResponse.CaloriesSummary(
                        c.recordDate, c.activeCalories, c.totalCalories, c.source)));

    // 機能見直し-1-#5: 睡眠・安静時心拍は直近4週間平均（QA Q5-5）を添えて返す
    LocalDate today = LocalDate.now();
    healthSleepService
        .getLatest(userId)
        .ifPresent(
            s -> {
              Optional<HealthAverage> avg =
                  healthSleepService.getAverageDurationMinutes(
                      userId, HealthSleepService.AVERAGE_WEEKS, today);
              response.setSleep(
                  new HealthSummaryResponse.SleepSummary(
                      s.sleepDate,
                      s.startTime,
                      s.endTime,
                      s.durationMinutes,
                      s.source,
                      avg.map(HealthAverage::average).orElse(null),
                      avg.map(HealthAverage::days).orElse(0)));
            });

    healthRestingHeartRateService
        .getLatest(userId)
        .ifPresent(
            r -> {
              Optional<HealthAverage> avg =
                  healthRestingHeartRateService.getAverage(
                      userId, HealthSleepService.AVERAGE_WEEKS, today);
              response.setRestingHeartRate(
                  new HealthSummaryResponse.RestingHeartRateSummary(
                      r.recordDate,
                      r.restingBpm,
                      r.source,
                      avg.map(HealthAverage::average).orElse(null),
                      avg.map(HealthAverage::days).orElse(0)));
            });

    return response;
  }
}
