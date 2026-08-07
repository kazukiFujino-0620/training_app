package com.example.traning.smarttrainer.recommendation;

import org.springframework.stereotype.Component;

/** 減量モード: PRの60-75%、12-15回、3セット。 */
@Component
public class CuttingStrategy implements RecommendationStrategy {

  @Override
  public GoalMode getMode() {
    return GoalMode.CUTTING;
  }

  @Override
  public double getWeightPctMin() {
    return 0.60;
  }

  @Override
  public double getWeightPctMax() {
    return 0.75;
  }

  @Override
  public int getRepsMin() {
    return 12;
  }

  @Override
  public int getRepsMax() {
    return 15;
  }

  @Override
  public int getSets() {
    return 3;
  }
}
