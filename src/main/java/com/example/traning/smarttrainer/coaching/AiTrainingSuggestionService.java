package com.example.traning.smarttrainer.coaching;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ita5-1 機能1: AIトレーニング提案。週頭（月曜）に1回、7日分をまとめてオンデマンド生成し、週次キャッシュする（案B踏襲、頻度は週次）。
 * 「反映」操作自体は1日分ずつ行う想定のため、呼び出し元は{@link #getTodayEntry(User)}で本日分のみを抽出して使う。
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
  private final TrainingMasterDao trainingMasterDao;
  private final TrainingCoach trainingCoach;
  private final PersonalRecordService personalRecordService;
  private final ObjectMapper objectMapper;

  public AiTrainingSuggestionService(
      AiTrainingSuggestionDao dao,
      TrainingMasterDao trainingMasterDao,
      TrainingCoach trainingCoach,
      PersonalRecordService personalRecordService,
      ObjectMapper objectMapper) {
    this.dao = dao;
    this.trainingMasterDao = trainingMasterDao;
    this.trainingCoach = trainingCoach;
    this.personalRecordService = personalRecordService;
    this.objectMapper = objectMapper;
  }

  /** 同意していないユーザーには{@code Optional.empty()}を返す（呼び出し元でAI機能の案内表示に使う）。 */
  @Transactional
  public Optional<List<AiSuggestedDay>> getOrGenerateThisWeekPlan(User user) {
    if (!Boolean.TRUE.equals(user.getAiAdviceConsent())) {
      return Optional.empty();
    }

    Long userId = user.getUserId().longValue();
    LocalDate weekStartDate = LocalDate.now().with(DayOfWeek.MONDAY);

    Optional<AiTrainingSuggestion> cached = dao.selectByUserIdAndWeekStart(userId, weekStartDate);
    if (cached.isPresent()) {
      return Optional.of(readDaysJson(cached.get().getItemsJson()));
    }

    Map<String, List<TrainingItemMaster>> masterItemsByPart = new LinkedHashMap<>();
    for (String part : FatigueCalculator.PART_ORDER) {
      masterItemsByPart.put(part, trainingMasterDao.selectItemsByPart(part));
    }

    Map<String, PersonalRecord> personalRecordsByItemName = new LinkedHashMap<>();
    for (PersonalRecord pr : personalRecordService.getByUserId(userId)) {
      personalRecordsByItemName.put(pr.getItemName(), pr);
    }

    List<AiSuggestedDay> days =
        trainingCoach.generateWeeklyPlan(
            weekStartDate, masterItemsByPart, personalRecordsByItemName);
    List<AiSuggestedDay> clampedDays =
        days.stream().map(day -> applySafetyClamp(userId, day)).toList();

    AiTrainingSuggestion entity = new AiTrainingSuggestion();
    entity.setUserId(userId);
    entity.setWeekStartDate(weekStartDate);
    entity.setItemsJson(writeDaysJson(clampedDays));
    entity.setSource(trainingCoach.source());
    dao.insert(entity);

    return Optional.of(clampedDays);
  }

  /**
   * 今日の分だけを抽出する（{@code /menu}表示・登録画面への反映で使用。反映操作自体は1日分ずつ行う）。 通常は必ず一致する（週次生成した7日分のいずれかに本日が含まれるため）。
   */
  @Transactional
  public Optional<AiSuggestedDay> getTodayEntry(User user) {
    return getOrGenerateThisWeekPlan(user).flatMap(this::findToday);
  }

  private Optional<AiSuggestedDay> findToday(List<AiSuggestedDay> days) {
    LocalDate today = LocalDate.now();
    return days.stream().filter(d -> d.date().isEqual(today)).findFirst();
  }

  /** 提案重量が既存PRの{@link #PR_CLAMP_RATIO}倍を超える場合、その倍率まで丸める。既存PRが無い種目はそのまま返す。 */
  private AiSuggestedDay applySafetyClamp(Long userId, AiSuggestedDay day) {
    if (day.items().isEmpty()) {
      return day;
    }
    List<AiSuggestedItem> clampedItems =
        day.items().stream().map(item -> applySafetyClamp(userId, item)).toList();
    return new AiSuggestedDay(
        day.date(),
        day.partCode(),
        day.partLabel(),
        day.comment(),
        clampedItems,
        day.restDayRecommended());
  }

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

  private String writeDaysJson(List<AiSuggestedDay> days) {
    try {
      return objectMapper.writeValueAsString(days);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("AIトレーニング提案のJSON変換に失敗しました", e);
    }
  }

  private List<AiSuggestedDay> readDaysJson(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<AiSuggestedDay>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("AIトレーニング提案のJSON復元に失敗しました", e);
    }
  }
}
