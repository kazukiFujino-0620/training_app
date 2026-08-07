package com.example.traning.smarttrainer.recommendation;

import org.springframework.stereotype.Component;

/** 維持モード: PRの70-80%、8-10回、3セット。 */
@Component
public class MaintenanceStrategy implements RecommendationStrategy {

  @Override
  public GoalMode getMode() {
    return GoalMode.MAINTENANCE;
  }

  @Override
  public double getWeightPctMin() {
    return 0.70;
  }

  @Override
  public double getWeightPctMax() {
    return 0.80;
  }

  @Override
  public int getRepsMin() {
    return 8;
  }

  @Override
  public int getRepsMax() {
    return 10;
  }

  @Override
  public int getSets() {
    return 3;
  }
}
