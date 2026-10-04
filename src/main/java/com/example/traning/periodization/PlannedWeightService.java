package com.example.traning.periodization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成長グラフへの計画値重ね合わせ（機能見直し-1-#3 詳細設計書3-4節、QA Q3-8）。
 *
 * <p>TrainingService から呼ばれるため、依存はDAOのみに限定している（PeriodizationService経由にすると
 * トレーナー関連Service等への依存が増え、循環依存の原因になりやすいため分離）。
 */
@Service
@RequiredArgsConstructor
public class PlannedWeightService {

  private final ProgramCycleDao programCycleDao;

  /**
   * 成長グラフの各週ラベルの代表日について、その日が属するサイクルの計画上の目標重量を返す。
   *
   * @param weekLabelDates 成長グラフのlabelsと同じ順・同じ件数の代表日
   * @return weekLabelDatesと同じ長さ。計画値が無い週はnull
   */
  @Transactional(readOnly = true)
  public List<Double> calculatePlannedWeights(
      Long userId,
      String itemName,
      LocalDate startDate,
      LocalDate endDate,
      List<LocalDate> weekLabelDates) {
    List<Double> result = new ArrayList<>();
    if (weekLabelDates == null || weekLabelDates.isEmpty()) {
      return result;
    }
    List<ProgramCycle> cycles =
        programCycleDao.selectOverlappingByUserId(userId, startDate, endDate);
    Map<Long, CyclePlan> plans = new HashMap<>();

    for (LocalDate date : weekLabelDates) {
      Double value = null;
      Optional<ProgramCycle> cycleOpt = findCycleContaining(cycles, date);
      if (cycleOpt.isPresent()) {
        ProgramCycle cycle = cycleOpt.get();
        CyclePlan plan = plans.computeIfAbsent(cycle.getId(), id -> loadPlan(id, itemName));
        value = plan.plannedWeight(PeriodizationService.weekNumberOf(cycle, date));
      }
      result.add(value);
    }
    return result;
  }

  private static Optional<ProgramCycle> findCycleContaining(
      List<ProgramCycle> cycles, LocalDate date) {
    // サイクル同士は期間が重ならない前提（新サイクル開始時に旧サイクルはARCHIVED/COMPLETED）。
    // 万一重なる場合は後から開始したサイクルを優先する。
    ProgramCycle found = null;
    for (ProgramCycle c : cycles) {
      LocalDate end = c.getStartDate().plusDays(7L * c.getTotalWeeks());
      if (!date.isBefore(c.getStartDate()) && date.isBefore(end)) {
        found = c;
      }
    }
    return Optional.ofNullable(found);
  }

  private CyclePlan loadPlan(Long cycleId, String itemName) {
    BigDecimal baseline =
        programCycleDao.selectBaselinesByCycleId(cycleId).stream()
            .filter(b -> itemName.equals(b.getItemName()))
            .map(ProgramCycleItemBaseline::getBaselineOneRm)
            .findFirst()
            .orElse(null);
    Map<Integer, BigDecimal> intensityByWeek =
        programCycleDao.selectWeeksByCycleId(cycleId).stream()
            .collect(
                Collectors.toMap(
                    ProgramCycleWeek::getWeekNumber, ProgramCycleWeek::getTargetIntensityPct));
    Set<Long> dayTemplateIdsWithItem =
        programCycleDao.selectItemsByCycleId(cycleId).stream()
            .filter(i -> itemName.equals(i.getItemName()))
            .map(ProgramCycleDayTemplateItem::getDayTemplateId)
            .collect(Collectors.toSet());
    Set<Integer> weeksWithItem =
        programCycleDao.selectDayTemplatesByCycleId(cycleId).stream()
            .filter(d -> dayTemplateIdsWithItem.contains(d.getId()))
            .map(ProgramCycleDayTemplate::getWeekNumber)
            .collect(Collectors.toSet());
    return new CyclePlan(baseline, intensityByWeek, weeksWithItem);
  }

  private record CyclePlan(
      BigDecimal baselineOneRm,
      Map<Integer, BigDecimal> intensityByWeek,
      Set<Integer> weeksWithItem) {

    Double plannedWeight(int weekNumber) {
      if (baselineOneRm == null || !weeksWithItem.contains(weekNumber)) return null;
      BigDecimal pct = intensityByWeek.get(weekNumber);
      if (pct == null) return null;
      return baselineOneRm
          .multiply(pct)
          .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP)
          .doubleValue();
    }
  }
}
