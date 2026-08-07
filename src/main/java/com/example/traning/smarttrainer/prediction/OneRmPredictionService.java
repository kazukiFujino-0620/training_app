package com.example.traning.smarttrainer.prediction;

import org.springframework.stereotype.Service;

/**
 * Epley式による推定1RM（最大挙上重量）計算。
 * スポーツ科学の公知の計算式（Epley, 1985）であり特許リスクなし。
 * [[2026-06-06-ai-menu-requirements]] Phase 2参照。
 */
@Service
public class OneRmPredictionService {

  /**
   * 推定1RMを計算する。
   *
   * @param weight 使用重量(kg)
   * @param reps   反復回数
   * @return 推定1RM(kg)。reps が 1 以下ならそのまま weight を返す。
   */
  public double estimateOneRm(double weight, int reps) {
    if (reps <= 1) return weight;
    return weight * (1.0 + reps / 30.0);
  }
}
