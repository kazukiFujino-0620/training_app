package com.example.traning.health;

import java.util.Arrays;
import java.util.Optional;

/**
 * 参考表示用の過去平均（機能見直し-1-#5）。
 *
 * @param average 平均値（四捨五入した整数。睡眠は分、安静時心拍はbpm）
 * @param days 平均の元になった記録日数
 */
public record HealthAverage(int average, int days) {

  static Optional<HealthAverage> of(int[] values) {
    if (values.length == 0) {
      return Optional.empty();
    }
    double avg = Arrays.stream(values).average().orElse(0);
    return Optional.of(new HealthAverage((int) Math.round(avg), values.length));
  }
}
