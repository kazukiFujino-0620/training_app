package com.example.traning.smarttrainer.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.traning.smarttrainer.recommendation.FatigueCalculator.FatigueResult;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ita5-3: 部位別疲労度・ボリューム集計からWARMUPセットを除外することを検証する。
 * TrainingDao・TrainingDetailDaoはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class FatigueCalculatorTest {

  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;

  private FatigueCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new FatigueCalculator(trainingDao, trainingDetailDao);
  }

  private Training training(long id, String partCode, LocalDate date) {
    Training t = new Training();
    t.setId(id);
    t.setPartCode(partCode);
    t.setTrainingDate(date);
    return t;
  }

  private TrainingDetail detail(String setType, double weight, int reps, boolean completed) {
    TrainingDetail d = new TrainingDetail();
    d.setSetType(setType);
    d.setWeight(weight);
    d.setReps(reps);
    d.setIsCompleted(completed);
    return d;
  }

  @Test
  void calculate_WARMUPセットはボリューム集計から除外するがセット数にはカウントする() {
    LocalDate today = LocalDate.of(2026, 8, 30);
    Training t = training(1L, "CHEST", today);
    when(trainingDao.selectByUserIdAndDateRange(8, today.minusDays(6), today))
        .thenReturn(List.of(t));
    when(trainingDetailDao.selectByTrainingId(1L))
        .thenReturn(
            List.of(
                detail("WARMUP", 20.0, 10, true), // 200kg分だが除外対象
                detail("MAIN", 90.0, 5, true), // 450kg
                detail("DROP", 70.0, 5, true) // 350kg（DROPは除外対象外）
                ));

    FatigueResult result = calculator.calculate(8L, today);

    // WARMUP分(200kg)を除いた 450 + 350 = 800kg のみが集計される
    assertThat(result.volumeByPart().get("CHEST")).isEqualTo(800L);
    // セット数はWARMUPも含めて3セットのまま
    assertThat(result.setsByPart().get("CHEST")).isEqualTo(3);
  }

  @Test
  void calculate_未完了セットは種別に関わらず除外する() {
    LocalDate today = LocalDate.of(2026, 8, 30);
    Training t = training(1L, "BACK", today);
    when(trainingDao.selectByUserIdAndDateRange(8, today.minusDays(6), today))
        .thenReturn(List.of(t));
    when(trainingDetailDao.selectByTrainingId(1L))
        .thenReturn(List.of(detail("MAIN", 60.0, 10, false)));

    FatigueResult result = calculator.calculate(8L, today);

    assertThat(result.volumeByPart().get("BACK")).isEqualTo(0L);
    assertThat(result.setsByPart().get("BACK")).isEqualTo(0);
  }
}
