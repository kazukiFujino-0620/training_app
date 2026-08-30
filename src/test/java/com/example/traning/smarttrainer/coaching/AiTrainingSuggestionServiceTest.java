package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.smarttrainer.recommendation.RecommendedItem;
import com.example.traning.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ita5-1 機能1: {@link AiTrainingSuggestionService}の同意ゲート・当日キャッシュ・安全性クランプ（案D）を検証する。
 * DAO・RecommendationService・TrainingCoach・PersonalRecordServiceはMockitoでモックし、
 * ObjectMapperのみ実インスタンスを使いJSONの往復を検証する。
 */
@ExtendWith(MockitoExtension.class)
class AiTrainingSuggestionServiceTest {

  @Mock private AiTrainingSuggestionDao dao;
  @Mock private RecommendationService recommendationService;
  @Mock private TrainingCoach trainingCoach;
  @Mock private PersonalRecordService personalRecordService;

  private AiTrainingSuggestionService service;

  @BeforeEach
  void setUp() {
    service =
        new AiTrainingSuggestionService(
            dao, recommendationService, trainingCoach, personalRecordService, new ObjectMapper());
  }

  private User user(int id, boolean consent) {
    return User.builder().userId(id).aiAdviceConsent(consent).build();
  }

  @Test
  void getOrGenerateTodaySuggestion_同意していない場合は空を返しDAOを呼ばない() {
    User user = user(1, false);

    Optional<AiTrainingSuggestionView> result = service.getOrGenerateTodaySuggestion(user);

    assertThat(result).isEmpty();
    verify(dao, never()).selectByUserIdAndDate(any(), any());
    verify(recommendationService, never()).getTodayRecommendation(any());
  }

  @Test
  void getOrGenerateTodaySuggestion_当日キャッシュがあればそれを返す() {
    User user = user(1, true);
    AiTrainingSuggestion cached = new AiTrainingSuggestion();
    cached.setComment("キャッシュ済みコメント");
    cached.setPartCode("CHEST");
    cached.setItemsJson(
        "[{\"itemName\":\"ベンチプレス\",\"weightMin\":60.0,\"weightMax\":80.0,\"repsMin\":5,\"repsMax\":10,\"sets\":3}]");
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.of(cached));

    Optional<AiTrainingSuggestionView> result = service.getOrGenerateTodaySuggestion(user);

    assertThat(result).isPresent();
    assertThat(result.get().comment()).isEqualTo("キャッシュ済みコメント");
    assertThat(result.get().items()).hasSize(1);
    assertThat(result.get().items().get(0).itemName()).isEqualTo("ベンチプレス");
    verify(recommendationService, never()).getTodayRecommendation(any());
    verify(trainingCoach, never()).generate(any());
  }

  @Test
  void getOrGenerateTodaySuggestion_キャッシュが無ければ生成して保存する() {
    User user = user(1, true);
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.empty());
    RecommendedItem item = new RecommendedItem("スクワット", 40.0, 60.0, 8, 12, 3);
    DailyRecommendation recommendation =
        new DailyRecommendation("LEG", "脚", "理由", List.of(item), false);
    when(recommendationService.getTodayRecommendation(1L)).thenReturn(recommendation);
    CoachingResult coachingResult =
        new CoachingResult(
            "生成コメント", List.of(new AiSuggestedItem("スクワット", 40.0, 60.0, 8, 12, 3)));
    when(trainingCoach.generate(recommendation)).thenReturn(coachingResult);
    when(trainingCoach.source()).thenReturn("mock");
    when(personalRecordService.getByUserIdAndItem(1L, "スクワット")).thenReturn(Optional.empty());

    Optional<AiTrainingSuggestionView> result = service.getOrGenerateTodaySuggestion(user);

    assertThat(result).isPresent();
    assertThat(result.get().comment()).isEqualTo("生成コメント");
    assertThat(result.get().items()).hasSize(1);
    assertThat(result.get().items().get(0).weightMax()).isEqualTo(60.0);

    ArgumentCaptor<AiTrainingSuggestion> captor = ArgumentCaptor.forClass(AiTrainingSuggestion.class);
    verify(dao).insert(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getPartCode()).isEqualTo("LEG");
    assertThat(captor.getValue().getSource()).isEqualTo("mock");
  }

  @Test
  void getOrGenerateTodaySuggestion_既存PRを超える提案は安全性クランプで丸められる() {
    User user = user(1, true);
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.empty());
    DailyRecommendation recommendation =
        new DailyRecommendation(
            "CHEST",
            "胸",
            "理由",
            List.of(new RecommendedItem("ベンチプレス", 90.0, 120.0, 5, 8, 3)),
            false);
    when(recommendationService.getTodayRecommendation(1L)).thenReturn(recommendation);
    when(trainingCoach.generate(recommendation))
        .thenReturn(
            new CoachingResult(
                "コメント", List.of(new AiSuggestedItem("ベンチプレス", 90.0, 120.0, 5, 8, 3))));
    when(trainingCoach.source()).thenReturn("mock");
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(80.0); // PRの110% = 88.0kg
    when(personalRecordService.getByUserIdAndItem(1L, "ベンチプレス")).thenReturn(Optional.of(pr));

    Optional<AiTrainingSuggestionView> result = service.getOrGenerateTodaySuggestion(user);

    assertThat(result).isPresent();
    AiSuggestedItem clamped = result.get().items().get(0);
    // PR 80.0kg × 110% = 88.0kg。提案の90.0〜120.0kgはどちらも上限を超えるため88.0kgに丸められる
    assertThat(clamped.weightMin()).isEqualTo(88.0);
    assertThat(clamped.weightMax()).isEqualTo(88.0);
  }

  @Test
  void getOrGenerateTodaySuggestion_休養日推奨の場合は空アイテムで保存する() {
    User user = user(1, true);
    when(dao.selectByUserIdAndDate(1L, LocalDate.now())).thenReturn(Optional.empty());
    DailyRecommendation restDay = DailyRecommendation.restDay();
    when(recommendationService.getTodayRecommendation(1L)).thenReturn(restDay);
    when(trainingCoach.generate(restDay)).thenReturn(new CoachingResult("休んでください", List.of()));
    when(trainingCoach.source()).thenReturn("mock");

    Optional<AiTrainingSuggestionView> result = service.getOrGenerateTodaySuggestion(user);

    assertThat(result).isPresent();
    assertThat(result.get().items()).isEmpty();
    verify(personalRecordService, never()).getByUserIdAndItem(any(), any());
  }
}
