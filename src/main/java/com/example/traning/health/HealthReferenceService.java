package com.example.traning.health;

import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Web版の「体調の参考情報」カード用に、睡眠・安静時心拍の最新値と直近4週間平均をまとめる（機能見直し-1-#5）。 */
@Service
@RequiredArgsConstructor
public class HealthReferenceService {

  private final HealthSleepService healthSleepService;
  private final HealthRestingHeartRateService healthRestingHeartRateService;

  @Transactional(readOnly = true)
  public HealthReferenceView getReference(Long userId, LocalDate today) {
    HealthReferenceView.Sleep sleep =
        healthSleepService
            .getLatest(userId)
            .map(
                s -> {
                  Optional<HealthAverage> avg =
                      healthSleepService.getAverageDurationMinutes(
                          userId, HealthSleepService.AVERAGE_WEEKS, today);
                  return new HealthReferenceView.Sleep(
                      s.sleepDate,
                      s.durationMinutes,
                      s.source,
                      avg.map(HealthAverage::average).orElse(null),
                      avg.map(HealthAverage::days).orElse(0));
                })
            .orElse(null);

    HealthReferenceView.RestingHeartRate rhr =
        healthRestingHeartRateService
            .getLatest(userId)
            .map(
                r -> {
                  Optional<HealthAverage> avg =
                      healthRestingHeartRateService.getAverage(
                          userId, HealthSleepService.AVERAGE_WEEKS, today);
                  return new HealthReferenceView.RestingHeartRate(
                      r.recordDate,
                      r.restingBpm,
                      r.source,
                      avg.map(HealthAverage::average).orElse(null),
                      avg.map(HealthAverage::days).orElse(0));
                })
            .orElse(null);

    return new HealthReferenceView(sleep, rhr);
  }
}
