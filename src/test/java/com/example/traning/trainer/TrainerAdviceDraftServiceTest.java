package com.example.traning.trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.coaching.AdviceCoach;
import com.example.traning.smarttrainer.coaching.AdviceContext;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ita5-1 機能2: {@link TrainerAdviceDraftService}の同意ゲート・宛先スコープ検証を確認する。 */
@ExtendWith(MockitoExtension.class)
class TrainerAdviceDraftServiceTest {

  @Mock private TrainerAdviceService trainerAdviceService;
  @Mock private RecommendationService recommendationService;
  @Mock private FatigueCalculator fatigueCalculator;
  @Mock private PersonalRecordService personalRecordService;
  @Mock private TrainingDao trainingDao;
  @Mock private AdviceCoach adviceCoach;

  private TrainerAdviceDraftService service;

  @BeforeEach
  void setUp() {
    service =
        new TrainerAdviceDraftService(
            trainerAdviceService,
            recommendationService,
            fatigueCalculator,
            personalRecordService,
            trainingDao,
            adviceCoach);
  }

  private User user(int id, String name, boolean consent) {
    return User.builder().userId(id).userName(name).aiAdviceConsent(consent).build();
  }

  @Test
  void generateDraft_トレーナーが同意していない場合は空を返し他の処理を行わない() {
    User trainer = user(1, "トレーナー", false);

    Optional<String> result = service.generateDraft(trainer, 2L, LocalDate.now());

    assertThat(result).isEmpty();
    verify(trainerAdviceService, never()).listTrainees(any());
    verify(adviceCoach, never()).generateDraft(any());
  }

  @Test
  void generateDraft_宛先がスコープ外の場合は例外を投げる() {
    User trainer = user(1, "トレーナー", true);
    when(trainerAdviceService.listTrainees(trainer)).thenReturn(List.of(user(3, "他人", false)));

    assertThatThrownBy(() -> service.generateDraft(trainer, 2L, LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void generateDraft_正常系ではコンテキストを組み立ててAdviceCoachに渡す() {
    User trainer = user(1, "トレーナー", true);
    User trainee = user(2, "トレーニー太郎", false);
    LocalDate targetDate = LocalDate.of(2026, 8, 31);
    when(trainerAdviceService.listTrainees(trainer)).thenReturn(List.of(trainee));
    when(trainingDao.selectByDate(2L, targetDate, targetDate)).thenReturn(List.of());
    DailyRecommendation recommendation = DailyRecommendation.restDay();
    when(recommendationService.getTodayRecommendation(2L)).thenReturn(recommendation);
    FatigueCalculator.FatigueResult fatigueResult =
        new FatigueCalculator.FatigueResult(
            new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
    when(fatigueCalculator.calculate(2L, LocalDate.now())).thenReturn(fatigueResult);
    when(personalRecordService.getByUserId(2L)).thenReturn(List.of());
    when(adviceCoach.generateDraft(any())).thenReturn("生成された下書き");

    Optional<String> result = service.generateDraft(trainer, 2L, targetDate);

    assertThat(result).contains("生成された下書き");
    ArgumentCaptor<AdviceContext> captor = ArgumentCaptor.forClass(AdviceContext.class);
    verify(adviceCoach).generateDraft(captor.capture());
    assertThat(captor.getValue().traineeName()).isEqualTo("トレーニー太郎");
    assertThat(captor.getValue().targetDate()).isEqualTo(targetDate);
    assertThat(captor.getValue().recommendation()).isEqualTo(recommendation);
  }
}
