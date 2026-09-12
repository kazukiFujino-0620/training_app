package com.example.traning.smarttrainer.coaching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.DayOfWeek;
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
 * ita5-1 機能1: {@link AiTrainingSuggestionService}の同意ゲート・週次キャッシュ・安全性クランプ（案D）を検証する。
 * DAO・TrainingMasterDao・TrainingCoach・PersonalRecordServiceはMockitoでモックし、
 * ObjectMapperのみ実インスタンスを使いJSONの往復を検証する。
 */
@ExtendWith(MockitoExtension.class)
class AiTrainingSuggestionServiceTest {

  @Mock private AiTrainingSuggestionDao dao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private TrainingCoach trainingCoach;
  @Mock private PersonalRecordService personalRecordService;

  private AiTrainingSuggestionService service;
  private final LocalDate today = LocalDate.now();
  private final LocalDate weekStart = today.with(DayOfWeek.MONDAY);

  @BeforeEach
  void setUp() {
    service =
        new AiTrainingSuggestionService(
            dao,
            trainingMasterDao,
            trainingCoach,
            personalRecordService,
            new ObjectMapper().registerModule(new JavaTimeModule()));
    lenient().when(trainingMasterDao.selectItemsByPart(anyString())).thenReturn(List.of());
    lenient().when(personalRecordService.getByUserId(any())).thenReturn(List.of());
  }

  private User user(int id, boolean consent) {
    return User.builder().userId(id).aiAdviceConsent(consent).build();
  }

  private AiSuggestedDay restDay(LocalDate date) {
    return new AiSuggestedDay(date, null, null, "休養日", List.of(), true);
  }

  @Test
  void getOrGenerateThisWeekPlan_同意していない場合は空を返しDAOを呼ばない() {
    User user = user(1, false);

    Optional<List<AiSuggestedDay>> result = service.getOrGenerateThisWeekPlan(user);

    assertThat(result).isEmpty();
    verify(dao, never()).selectByUserIdAndWeekStart(any(), any());
    verify(trainingCoach, never()).generateWeeklyPlan(any(), any(), any());
  }

  @Test
  void getOrGenerateThisWeekPlan_今週分のキャッシュがあればそれを返す() throws Exception {
    User user = user(1, true);
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    List<AiSuggestedDay> cachedDays =
        List.of(
            new AiSuggestedDay(
                weekStart,
                "CHEST",
                "胸",
                "キャッシュ済みコメント",
                List.of(new AiSuggestedItem("ベンチプレス", 60.0, 80.0, 5, 10, 3)),
                false));
    AiTrainingSuggestion cached = new AiTrainingSuggestion();
    cached.setItemsJson(mapper.writeValueAsString(cachedDays));
    when(dao.selectByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.of(cached));

    Optional<List<AiSuggestedDay>> result = service.getOrGenerateThisWeekPlan(user);

    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(1);
    assertThat(result.get().get(0).comment()).isEqualTo("キャッシュ済みコメント");
    verify(trainingCoach, never()).generateWeeklyPlan(any(), any(), any());
    verify(dao, never()).insert(any());
  }

  @Test
  void getOrGenerateThisWeekPlan_キャッシュが無ければ生成して保存する() {
    User user = user(1, true);
    when(dao.selectByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());
    List<AiSuggestedDay> generatedDays =
        List.of(
            new AiSuggestedDay(
                weekStart,
                "CHEST",
                "胸",
                "生成コメント",
                List.of(new AiSuggestedItem("ベンチプレス", 20.0, 30.0, 8, 10, 3)),
                false),
            restDay(weekStart.plusDays(1)));
    when(trainingCoach.generateWeeklyPlan(any(), any(), any())).thenReturn(generatedDays);
    when(trainingCoach.source()).thenReturn("mock");
    when(personalRecordService.getByUserIdAndItem(1L, "ベンチプレス")).thenReturn(Optional.empty());

    Optional<List<AiSuggestedDay>> result = service.getOrGenerateThisWeekPlan(user);

    assertThat(result).isPresent();
    assertThat(result.get()).hasSize(2);
    assertThat(result.get().get(0).comment()).isEqualTo("生成コメント");

    ArgumentCaptor<AiTrainingSuggestion> captor =
        ArgumentCaptor.forClass(AiTrainingSuggestion.class);
    verify(dao).insert(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getWeekStartDate()).isEqualTo(weekStart);
    assertThat(captor.getValue().getSource()).isEqualTo("mock");
  }

  @Test
  void getOrGenerateThisWeekPlan_既存PRを超える提案は安全性クランプで丸められる() {
    User user = user(1, true);
    when(dao.selectByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());
    List<AiSuggestedDay> generatedDays =
        List.of(
            new AiSuggestedDay(
                weekStart,
                "CHEST",
                "胸",
                "コメント",
                List.of(new AiSuggestedItem("ベンチプレス", 90.0, 120.0, 5, 8, 3)),
                false));
    when(trainingCoach.generateWeeklyPlan(any(), any(), any())).thenReturn(generatedDays);
    when(trainingCoach.source()).thenReturn("mock");
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(80.0); // PRの110% = 88.0kg
    when(personalRecordService.getByUserIdAndItem(1L, "ベンチプレス")).thenReturn(Optional.of(pr));

    Optional<List<AiSuggestedDay>> result = service.getOrGenerateThisWeekPlan(user);

    assertThat(result).isPresent();
    AiSuggestedItem clamped = result.get().get(0).items().get(0);
    // PR 80.0kg × 110% = 88.0kg。提案の90.0〜120.0kgはどちらも上限を超えるため88.0kgに丸められる
    assertThat(clamped.weightMin()).isEqualTo(88.0);
    assertThat(clamped.weightMax()).isEqualTo(88.0);
  }

  @Test
  void getOrGenerateThisWeekPlan_休養日はPR照会を行わない() {
    User user = user(1, true);
    when(dao.selectByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());
    when(trainingCoach.generateWeeklyPlan(any(), any(), any()))
        .thenReturn(List.of(restDay(weekStart)));
    when(trainingCoach.source()).thenReturn("mock");

    Optional<List<AiSuggestedDay>> result = service.getOrGenerateThisWeekPlan(user);

    assertThat(result).isPresent();
    assertThat(result.get().get(0).items()).isEmpty();
    verify(personalRecordService, never()).getByUserIdAndItem(any(), any());
  }

  @Test
  void getTodayEntry_週次プランから本日分だけを抽出する() {
    User user = user(1, true);
    List<AiSuggestedDay> generatedDays =
        List.of(new AiSuggestedDay(today, "BACK", "背中", "今日のコメント", List.of(), false));
    when(dao.selectByUserIdAndWeekStart(1L, weekStart)).thenReturn(Optional.empty());
    when(trainingCoach.generateWeeklyPlan(any(), any(), any())).thenReturn(generatedDays);
    when(trainingCoach.source()).thenReturn("mock");

    Optional<AiSuggestedDay> result = service.getTodayEntry(user);

    assertThat(result).isPresent();
    assertThat(result.get().date()).isEqualTo(today);
    assertThat(result.get().comment()).isEqualTo("今日のコメント");
  }
}
