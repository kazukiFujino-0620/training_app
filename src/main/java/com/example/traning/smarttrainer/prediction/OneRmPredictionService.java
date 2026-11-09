package com.example.traning.smarttrainer.prediction;

import org.springframework.stereotype.Service;

/**
 * Epley式による推定1RM（最大挙上重量）計算。 スポーツ科学の公知の計算式（Epley, 1985）であり特許リスクなし。
 * [[2026-06-06-ai-menu-requirements]] Phase 2参照。
 */
@Service
public class OneRmPredictionService {

  /**
   * 推定1RMを計算する。
   *
   * @param weight 使用重量(kg)
   * @param reps 反復回数
   * @return 推定1RM(kg)。reps が 1 以下ならそのまま weight を返す。
   */
  public double estimateOneRm(double weight, int reps) {
    if (reps <= 1) return weight;
    return weight * (1.0 + reps / 30.0);
  }

  /**
   * Epley式の逆算で、推定1RMに対する割合 intensity の重量を挙げられる最大回数を返す（小数のまま）。
   *
   * @param intensity 推定1RMに対する割合（0より大きく1以下）
   */
  public double maxRepsAtIntensity(double intensity) {
    if (intensity <= 0) return 0;
    if (intensity >= 1.0) return 1.0;
    return 30.0 * (1.0 / intensity - 1.0);
  }
}
