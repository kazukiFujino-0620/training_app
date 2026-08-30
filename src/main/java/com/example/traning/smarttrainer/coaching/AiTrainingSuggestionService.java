package com.example.traning.smarttrainer.coaching;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ita5-1 機能1: AIトレーニング提案。オンデマンド生成＋当日キャッシュ（案B）。
 *
 * <p>安全性チェック（案D）: 生成された重量が既存PRの{@link #PR_CLAMP_RATIO}倍を超える場合はクランプする。
 * フロント側では別途「AIの提案です。無理のない範囲で調整してください」という警告文言を常に表示する想定（UI側の責務）。
 */
@Service
@Slf4j
public class AiTrainingSuggestionService {

  /** 安全性チェック案D: 既存PRの何倍までを許容するか。 */
  private static final double PR_CLAMP_RATIO = 1.1;

  private final AiTrainingSuggestionDao dao;
  private final RecommendationService recommendationService;
  private final TrainingCoach trainingCoach;
  private final PersonalRecordService personalRecordService;
  private final ObjectMapper objectMapper;

  public AiTrainingSuggestionService(
      AiTrainingSuggestionDao dao,
      RecommendationService recommendationService,
      TrainingCoach trainingCoach,
      PersonalRecordService personalRecordService,
      ObjectMapper objectMapper) {
    this.dao = dao;
    this.recommendationService = recommendationService;
    this.trainingCoach = trainingCoach;
    this.personalRecordService = personalRecordService;
    this.objectMapper = objectMapper;
  }

  /** 同意していないユーザーには{@code Optional.empty()}を返す（呼び出し元でAI機能の案内表示に使う）。 */
  @Transactional
  public Optional<AiTrainingSuggestionView> getOrGenerateTodaySuggestion(User user) {
    if (!Boolean.TRUE.equals(user.getAiAdviceConsent())) {
      return Optional.empty();
    }

    Long userId = user.getUserId().longValue();
    LocalDate today = LocalDate.now();

    Optional<AiTrainingSuggestion> cached = dao.selectByUserIdAndDate(userId, today);
    if (cached.isPresent()) {
      return Optional.of(toView(cached.get()));
    }

    DailyRecommendation recommendation = recommendationService.getTodayRecommendation(userId);
    CoachingResult result = trainingCoach.generate(recommendation);
    List<AiSuggestedItem> clampedItems =
        result.items().stream().map(item -> applySafetyClamp(userId, item)).toList();

    AiTrainingSuggestion entity = new AiTrainingSuggestion();
    entity.setUserId(userId);
    entity.setTargetDate(today);
    entity.setPartCode(recommendation.partCode());
    entity.setComment(result.comment());
    entity.setItemsJson(writeItemsJson(clampedItems));
    entity.setSource(trainingCoach.source());
    dao.insert(entity);

    return Optional.of(new AiTrainingSuggestionView(result.comment(), recommendation.partCode(), clampedItems));
  }

  /** 提案重量が既存PRの{@link #PR_CLAMP_RATIO}倍を超える場合、その倍率まで丸める。既存PRが無い種目はそのまま返す。 */
  private AiSuggestedItem applySafetyClamp(Long userId, AiSuggestedItem item) {
    Optional<PersonalRecord> pr = personalRecordService.getByUserIdAndItem(userId, item.itemName());
    if (pr.isEmpty() || pr.get().getMaxWeight() == null) {
      return item;
    }

    double cap = pr.get().getMaxWeight() * PR_CLAMP_RATIO;
    double clampedMin = Math.min(item.weightMin(), cap);
    double clampedMax = Math.min(item.weightMax(), cap);
    if (clampedMin == item.weightMin() && clampedMax == item.weightMax()) {
      return item;
    }

    log.info(
        "AIトレーニング提案の安全性クランプ適用 - userId={}, item={}, 提案={}〜{}kg, PR上限={}kg",
        userId,
        item.itemName(),
        item.weightMin(),
        item.weightMax(),
        cap);
    return new AiSuggestedItem(
        item.itemName(), clampedMin, clampedMax, item.repsMin(), item.repsMax(), item.sets());
  }

  private AiTrainingSuggestionView toView(AiTrainingSuggestion entity) {
    return new AiTrainingSuggestionView(
        entity.getComment(), entity.getPartCode(), readItemsJson(entity.getItemsJson()));
  }

  private String writeItemsJson(List<AiSuggestedItem> items) {
    try {
      return objectMapper.writeValueAsString(items);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("AIトレーニング提案のJSON変換に失敗しました", e);
    }
  }

  private List<AiSuggestedItem> readItemsJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<AiSuggestedItem>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("AIトレーニング提案のJSON復元に失敗しました", e);
    }
  }
}
