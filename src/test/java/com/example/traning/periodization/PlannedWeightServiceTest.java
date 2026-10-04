package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 機能見直し-1-#3 QA Q3-8: 成長グラフの計画値算出。 */
@ExtendWith(MockitoExtension.class)
class PlannedWeightServiceTest {

  @Mock private ProgramCycleDao programCycleDao;
  private PlannedWeightService service;

  private static final LocalDate START = LocalDate.of(2026, 9, 1);

  @BeforeEach
  void setUp() {
    service = new PlannedWeightService(programCycleDao);
  }

  private void stubCycle() {
    ProgramCycle c = new ProgramCycle();
    c.setId(10L);
    c.setStartDate(START);
    c.setTotalWeeks(4);
    when(programCycleDao.selectOverlappingByUserId(eq(1L), any(), any())).thenReturn(List.of(c));

    ProgramCycleItemBaseline b = new ProgramCycleItemBaseline();
    b.setItemName("ベンチプレス");
    b.setBaselineOneRm(new BigDecimal("100.0"));
    when(programCycleDao.selectBaselinesByCycleId(10L)).thenReturn(List.of(b));

    when(programCycleDao.selectWeeksByCycleId(10L))
        .thenReturn(List.of(week(1, "70.0"), week(2, "75.0"), week(3, "80.0"), week(4, "60.0")));

    // 1〜3週目はベンチプレスあり、4週目（ディロード）は種目なし
    when(programCycleDao.selectDayTemplatesByCycleId(10L))
        .thenReturn(List.of(day(101L, 1), day(102L, 2), day(103L, 3), day(104L, 4)));
    when(programCycleDao.selectItemsByCycleId(10L))
        .thenReturn(
            List.of(
                item(101L, "ベンチプレス"),
                item(102L, "ベンチプレス"),
                item(103L, "ベンチプレス"),
                item(104L, "スクワット")));
  }

  private static ProgramCycleWeek week(int n, String pct) {
    ProgramCycleWeek w = new ProgramCycleWeek();
    w.setWeekNumber(n);
    w.setTargetIntensityPct(new BigDecimal(pct));
    return w;
  }

  private static ProgramCycleDayTemplate day(Long id, int week) {
    ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
    d.setId(id);
    d.setWeekNumber(week);
    d.setDayOfWeek("MON");
    return d;
  }

  private static ProgramCycleDayTemplateItem item(Long dayId, String name) {
    ProgramCycleDayTemplateItem i = new ProgramCycleDayTemplateItem();
    i.setDayTemplateId(dayId);
    i.setItemName(name);
    return i;
  }

  @Test
  void 週ごとに基準1RMと目標強度の積を返し予定の無い週とサイクル外はnull() {
    stubCycle();
    List<LocalDate> labels =
        List.of(
            START.minusDays(7), // サイクル開始前
            START, // 1週目
            START.plusDays(8), // 2週目
            START.plusDays(15), // 3週目
            START.plusDays(22), // 4週目（種目なし）
            START.plusDays(29)); // サイクル終了後
    List<Double> result =
        service.calculatePlannedWeights(
            1L, "ベンチプレス", START.minusDays(30), START.plusDays(40), labels);
    assertThat(result).containsExactly(null, 70.0, 75.0, 80.0, null, null);
  }

  @Test
  void 基準1RMが無い種目は全件null() {
    stubCycle();
    List<Double> result =
        service.calculatePlannedWeights(
            1L, "デッドリフト", START, START.plusDays(27), List.of(START, START.plusDays(8)));
    assertThat(result).containsExactly(null, null);
  }

  @Test
  void ラベルが空ならDAOを呼ばない() {
    assertThat(service.calculatePlannedWeights(1L, "ベンチプレス", START, START, List.of())).isEmpty();
    verify(programCycleDao, never()).selectOverlappingByUserId(anyLong(), any(), any());
  }
}
