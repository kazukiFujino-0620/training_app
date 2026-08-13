package com.example.traning.health;

import com.example.traning.body.BodyMeasurement;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.mobile.dto.HealthSummaryResponse;
import com.example.traning.mobile.dto.HealthSyncRequest;
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
  private final HealthSleepDao healthSleepDao;

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
        syncSleep(userId, r, source);
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

  private void syncSleep(Long userId, HealthSyncRequest.SleepRecord r, String source) {
    Optional<HealthSleep> existing =
        healthSleepDao.selectByUserIdAndDateAndSource(userId, r.getDate(), source);
    if (existing.isPresent()) {
      HealthSleep e = existing.get();
      e.startTime = r.getStartTime();
      e.endTime = r.getEndTime();
      e.durationMinutes = r.getDurationMinutes();
      healthSleepDao.update(e);
    } else {
      HealthSleep e = new HealthSleep();
      e.userId = userId;
      e.sleepDate = r.getDate();
      e.startTime = r.getStartTime();
      e.endTime = r.getEndTime();
      e.durationMinutes = r.getDurationMinutes();
      e.source = source;
      healthSleepDao.insert(e);
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

    healthSleepDao
        .selectLatestByUserId(userId)
        .ifPresent(
            s ->
                response.setSleep(
                    new HealthSummaryResponse.SleepSummary(
                        s.sleepDate, s.startTime, s.endTime, s.durationMinutes, s.source)));

    return response;
  }
}
