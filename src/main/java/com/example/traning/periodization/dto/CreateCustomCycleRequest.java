package com.example.traning.periodization.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** 白紙から期分けサイクルを組むリクエスト（Web用、QA Q3-6見直しで新設）。 */
public record CreateCustomCycleRequest(
    @NotNull String name, @NotNull Integer totalWeeks, @NotNull List<Week> weeks, List<Day> days) {

  public record Week(Integer weekNumber, BigDecimal targetIntensityPct, Boolean deload) {}

  public record Day(Integer weekNumber, String dayOfWeek, String partCode, List<Item> items) {}

  public record Item(String itemName, Integer targetSets) {}
}
