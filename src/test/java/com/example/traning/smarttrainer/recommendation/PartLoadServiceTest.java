package com.example.traning.smarttrainer.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 機能見直し-1-#19: スタッフ向けの会員詳細の「部位別の直近負荷」が、会員本人のトップと同じ計算（{@link FatigueCalculator}）・同じ行の形で 作られることを検証する。
 */
@ExtendWith(MockitoExtension.class)
class PartLoadServiceTest {

  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private TrainingMasterDao trainingMasterDao;

  private FatigueCalculator calculator;
  private PartLoadService service;

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

  @BeforeEach
  void setUp() {
    calculator = new FatigueCalculator(trainingDao, trainingDetailDao);
    service = new PartLoadService(calculator, trainingMasterDao);
  }

  private static Training training(long id, String partCode, LocalDate date) {
    Training t = new Training();
    t.setId(id);
    t.setPartCode(partCode);
    t.setTrainingDate(date);
    return t;
  }

  private static TrainingDetail detail(double weight, int reps) {
    TrainingDetail d = new TrainingDetail();
    d.setSetType("MAIN");
    d.setWeight(weight);
    d.setReps(reps);
    d.setIsCompleted(true);
    return d;
  }

  private static TrainingMaster part(String code, String name) {
    TrainingMaster m = new TrainingMaster();
    m.setPartCode(code);
    m.setPartName(name);
    return m;
  }

  private void givenRecords() {
    when(trainingDao.selectByUserIdAndDateRange(9201, TODAY.minusDays(6), TODAY))
        .thenReturn(
            List.of(
                training(1L, "LEG", TODAY.minusDays(1)),
                training(2L, "CHEST", TODAY.minusDays(4))));
    when(trainingDetailDao.selectByTrainingId(1L))
        .thenReturn(List.of(detail(80, 10), detail(80, 10), detail(80, 10)));
    when(trainingDetailDao.selectByTrainingId(2L)).thenReturn(List.of(detail(50, 10)));
    when(trainingMasterDao.selectAllParts())
        .thenReturn(
            List.of(
                part("CHEST", "胸"),
                part("BACK", "背中"),
                part("SHOULDER", "肩"),
                part("ARM", "腕"),
                part("LEG", "脚"),
                part("LEG", "脚（重複）")));
  }

  @Test
  void loadRows_FatigueCalculatorの結果を部位の順で表示用の行にする() {
    givenRecords();

    List<Map<String, Object>> rows = service.loadRows(9201L, TODAY);

    assertThat(rows)
        .extracting(r -> r.get("partCode"))
        .containsExactly(FatigueCalculator.PART_ORDER);
    assertThat(rows).extracting(r -> r.get("partName")).containsExactly("胸", "背中", "肩", "腕", "脚");
    Map<String, Object> leg = rows.get(4);
    assertThat(leg.get("volume")).isEqualTo(2400L);
    assertThat(leg.get("sets")).isEqualTo(3);
    // 1日前: 0.5^(1/2) ≒ 71%
    assertThat(leg.get("pct")).isEqualTo(71);
    Map<String, Object> chest = rows.get(0);
    assertThat(chest.get("volume")).isEqualTo(500L);
    // 4日前: 0.5^2 = 25%
    assertThat(chest.get("pct")).isEqualTo(25);
    Map<String, Object> shoulder = rows.get(2);
    assertThat(shoulder.get("volume")).isEqualTo(0L);
    assertThat(shoulder.get("sets")).isEqualTo(0);
    assertThat(shoulder.get("pct")).isEqualTo(0);
  }

  @Test
  void loadRows_会員本人のトップと同じ計算結果になる() {
    givenRecords();

    List<Map<String, Object>> staffRows = service.loadRows(9201L, TODAY);
    // 会員本人のトップ（MenuController）は FatigueCalculator の結果を PartLoadService.toRows で行にしている
    List<Map<String, Object>> memberRows =
        PartLoadService.toRows(
            calculator.calculate(9201L, TODAY), trainingMasterDao.selectAllParts());

    assertThat(staffRows).isEqualTo(memberRows);
  }

  @Test
  void toRows_部位名がマスタにない部位はコードをそのまま出す() {
    FatigueCalculator.FatigueResult result =
        new FatigueCalculator.FatigueResult(
            Map.of("ARM", 10), Map.of("ARM", 100L), Map.of("ARM", 2));

    List<Map<String, Object>> rows = PartLoadService.toRows(result, List.of());

    assertThat(rows.get(3))
        .containsEntry("partCode", "ARM")
        .containsEntry("partName", "ARM")
        .containsEntry("volume", 100L)
        .containsEntry("sets", 2)
        .containsEntry("pct", 10);
  }
}
