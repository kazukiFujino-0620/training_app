package com.example.traning.health;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 安静時心拍の手動入力・自動同期（機能見直し-1-#5）。
 *
 * <p>体重記録（BodyMeasurementService）と同じく1日1行の上書き方式。手動入力・同期のどちらから書き込んでも、
 * 同日の既存データがあれば同期元を問わず上書きし、source列には最後に書き込んだ経路を残す。
 */
@Service
@RequiredArgsConstructor
public class HealthRestingHeartRateService {

  public static final String SOURCE_MANUAL = "MANUAL";

  private final HealthRestingHeartRateDao healthRestingHeartRateDao;

  /**
   * 手動入力。範囲は{@link HealthInputLimits}に従い、確認範囲の値はconfirmed=trueのときのみ保存する。
   *
   * @throws HealthInputRejectedException 入力エラー範囲、または確認範囲で未確認の場合（保存しない）
   */
  @Transactional
  public void saveManual(Long userId, LocalDate date, int restingBpm, boolean confirmed) {
    HealthInputRejectedException.throwIfRejected(
        HealthInputLimits.checkRestingBpm(restingBpm),
        confirmed,
        HealthInputLimits.RESTING_BPM_ERROR_MESSAGE);
    upsert(userId, date, restingBpm, SOURCE_MANUAL);
  }

  /** 自動同期。値の確認は出さず、そのまま保存する（入力エラー範囲の値は平均の計算でのみ除外する）。 */
  @Transactional
  public void syncFromHealthPlatform(Long userId, LocalDate date, int restingBpm, String source) {
    upsert(userId, date, restingBpm, source);
  }

  private void upsert(Long userId, LocalDate date, int restingBpm, String source) {
    Optional<HealthRestingHeartRate> existing =
        healthRestingHeartRateDao.selectByUserIdAndDate(userId, date);
    if (existing.isPresent()) {
      HealthRestingHeartRate e = existing.get();
      e.restingBpm = restingBpm;
      e.source = source;
      healthRestingHeartRateDao.update(e);
    } else {
      HealthRestingHeartRate e = new HealthRestingHeartRate();
      e.userId = userId;
      e.recordDate = date;
      e.restingBpm = restingBpm;
      e.source = source;
      healthRestingHeartRateDao.insert(e);
    }
  }

  @Transactional(readOnly = true)
  public Optional<HealthRestingHeartRate> getLatest(Long userId) {
    return healthRestingHeartRateDao.selectLatestByUserId(userId);
  }

  @Transactional(readOnly = true)
  public Optional<HealthRestingHeartRate> getByDate(Long userId, LocalDate date) {
    return healthRestingHeartRateDao.selectByUserIdAndDate(userId, date);
  }

  /**
   * 過去N週間（today-N週 〜 today-1日）の記録の平均（bpm、四捨五入）と記録日数。記録が無ければempty。
   *
   * <p>QA Q5-5で過去4週間に確定。呼び出し側は{@link HealthSleepService#AVERAGE_WEEKS}を渡す。 自動同期で入った入力エラー範囲（{@link
   * HealthInputLimits#isRestingBpmUsableForAverage}）の値は計算から除外する。
   */
  @Transactional(readOnly = true)
  public Optional<HealthAverage> getAverage(Long userId, int weeks, LocalDate today) {
    List<HealthRestingHeartRate> records =
        healthRestingHeartRateDao.selectByUserIdAndDateRange(
            userId, today.minusWeeks(weeks), today.minusDays(1));
    return HealthAverage.of(
        records.stream()
            .mapToInt(r -> r.restingBpm)
            .filter(HealthInputLimits::isRestingBpmUsableForAverage)
            .toArray());
  }
}
