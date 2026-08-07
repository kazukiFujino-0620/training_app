package com.example.traning.smarttrainer.recommendation;

/** 目的モード別の重量・回数・セット数レンジを提供する。 [[2026-06-06-ai-menu-requirements]] 「重量・セット数（モード別）」表に準拠。 */
public interface RecommendationStrategy {

  GoalMode getMode();

  /** PRに対する重量レンジの下限比率（0.0-1.0） */
  double getWeightPctMin();

  /** PRに対する重量レンジの上限比率（0.0-1.0） */
  double getWeightPctMax();

  int getRepsMin();

  int getRepsMax();

  int getSets();
}
