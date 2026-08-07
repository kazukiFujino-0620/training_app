package com.example.traning.smarttrainer.recommendation;

import org.springframework.stereotype.Component;

/** 筋肥大モード: PRの70-85%、8-12回、3-4セット（本実装は上限の4セットを採用）。 */
@Component
public class BulkingStrategy implements RecommendationStrategy {

  @Override
  public GoalMode getMode() {
    return GoalMode.BULKING;
  }

  @Override
  public double getWeightPctMin() {
    return 0.70;
  }

  @Override
  public double getWeightPctMax() {
    return 0.85;
  }

  @Override
  public int getRepsMin() {
    return 8;
  }

  @Override
  public int getRepsMax() {
    return 12;
  }

  @Override
  public int getSets() {
    return 4;
  }
}
