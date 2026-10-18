package com.example.traning.health;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 睡眠の手動入力・自動同期（機能見直し-1-#5）。
 *
 * <p>体重記録（BodyMeasurementService）と同じく1日1行の上書き方式（V43でUNIQUE制約を(user_id, sleep_date)に変更）。
 * 手動入力・同期のどちらから書き込んでも、同日の既存データがあれば同期元を問わず上書きし、source列には最後に書き込んだ経路を残す。
 */
@Service
@RequiredArgsConstructor
public class HealthSleepService {

  public static final String SOURCE_MANUAL = "MANUAL";

  /** 参考表示で比較する過去平均の期間（QA Q5-5で過去4週間に確定）。 */
  public static final int AVERAGE_WEEKS = 4;

  private final HealthSleepDao healthSleepDao;

  /**
   * 手動入力（前夜の主な睡眠。昼寝は含めない）。睡眠時間（分）のみを受け付け、寝た時刻・起きた時刻は空にする（同日の自動同期データを上書きした場合も空になる）。
   *
   * <p>0分（徹夜）も保存できる。範囲は{@link HealthInputLimits}に従い、確認範囲の値はconfirmed=trueのときのみ保存する。
   *
   * @throws HealthInputRejectedException 入力エラー範囲、または確認範囲で未確認の場合（保存しない）
   */
  @Transactional
  public void saveManual(Long userId, LocalDate date, int durationMinutes, boolean confirmed) {
    HealthInputRejectedException.throwIfRejected(
        HealthInputLimits.checkSleepMinutes(durationMinutes),
        confirmed,
        HealthInputLimits.SLEEP_ERROR_MESSAGE);
    upsert(userId, date, null, null, durationMinutes, SOURCE_MANUAL);
  }

  @Transactional
  public void syncFromHealthPlatform(
      Long userId,
      LocalDate date,
      LocalDateTime start,
      LocalDateTime end,
      int durationMinutes,
      String source) {
    upsert(userId, date, start, end, durationMinutes, source);
  }

  private void upsert(
      Long userId,
      LocalDate date,
      LocalDateTime start,
      LocalDateTime end,
      int durationMinutes,
      String source) {
    Optional<HealthSleep> existing = healthSleepDao.selectByUserIdAndDate(userId, date);
    if (existing.isPresent()) {
      HealthSleep e = existing.get();
      e.startTime = start;
      e.endTime = end;
      e.durationMinutes = durationMinutes;
      e.source = source;
      healthSleepDao.update(e);
    } else {
      HealthSleep e = new HealthSleep();
      e.userId = userId;
      e.sleepDate = date;
      e.startTime = start;
      e.endTime = end;
      e.durationMinutes = durationMinutes;
      e.source = source;
      healthSleepDao.insert(e);
    }
  }

  @Transactional(readOnly = true)
  public Optional<HealthSleep> getLatest(Long userId) {
    return healthSleepDao.selectLatestByUserId(userId);
  }

  @Transactional(readOnly = true)
  public Optional<HealthSleep> getByDate(Long userId, LocalDate date) {
    return healthSleepDao.selectByUserIdAndDate(userId, date);
  }

  /** 過去N週間（today-N週 〜 today-1日）の睡眠時間の平均（分、四捨五入）と記録日数。記録が無ければempty。 */
  @Transactional(readOnly = true)
  public Optional<HealthAverage> getAverageDurationMinutes(
      Long userId, int weeks, LocalDate today) {
    List<HealthSleep> records =
        healthSleepDao.selectByUserIdAndDateRange(
            userId, today.minusWeeks(weeks), today.minusDays(1));
    return HealthAverage.of(records.stream().mapToInt(r -> r.durationMinutes).toArray());
  }
}
