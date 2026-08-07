package com.example.traning.smarttrainer.recommendation;

/** ユーザーの目的モード。 */
public enum GoalMode {
  BULKING,
  CUTTING,
  MAINTENANCE;

  public static GoalMode fromString(String value) {
    if (value == null) return MAINTENANCE;
    try {
      return GoalMode.valueOf(value);
    } catch (IllegalArgumentException e) {
      return MAINTENANCE;
    }
  }
}
