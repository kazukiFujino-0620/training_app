package com.example.traning.training.controller;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.smarttrainer.coaching.AiFatigueCommentService;
import com.example.traning.smarttrainer.coaching.AiTrainingSuggestionService;
import com.example.traning.smarttrainer.prediction.AcwrService;
import com.example.traning.smarttrainer.prediction.ChurnDetectionService;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.smarttrainer.recommendation.RecommendedItem;
import com.example.traning.trainer.TrainerAdvice;
import com.example.traning.trainer.TrainerAdviceService;
import com.example.traning.training.SetType;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dto.PreviousTrainingResponse;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import com.example.traning.training.service.TrainingStatsService;
import com.example.traning.user.User;
import com.example.traning.weekly.WeeklyProgram;
import java.security.Principal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Validated
@Slf4j
@PreAuthorize("isAuthenticated()")
public class MenuController {

  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final TrainingMasterDao trainingMasterDao;
  private final TrainingService trainingService;
  private final CalorieCalculator calorieCalculator;
  private final RecommendationService recommendationService;
  private final OneRmPredictionService oneRmPredictionService;
  private final AcwrService acwrService;
  private final ChurnDetectionService churnDetectionService;
  private final com.example.traning.notice.NoticeService noticeService;
  private final TrainerAdviceService trainerAdviceService;
  private final AiTrainingSuggestionService aiTrainingSuggestionService;
  private final AiFatigueCommentService aiFatigueCommentService;
  private final FatigueCalculator fatigueCalculator;
  private final TrainingStatsService trainingStatsService;

  public MenuController(
      TrainingDao trainingDao,
      TrainingDetailDao trainingDetailDao,
      TrainingMasterDao trainingMasterDao,
      TrainingService trainingService,
      CalorieCalculator calorieCalculator,
      RecommendationService recommendationService,
      OneRmPredictionService oneRmPredictionService,
      AcwrService acwrService,
      ChurnDetectionService churnDetectionService,
      com.example.traning.notice.NoticeService noticeService,
      TrainerAdviceService trainerAdviceService,
      AiTrainingSuggestionService aiTrainingSuggestionService,
      AiFatigueCommentService aiFatigueCommentService,
      FatigueCalculator fatigueCalculator,
      TrainingStatsService trainingStatsService) {
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.trainingMasterDao = trainingMasterDao;
    this.trainingService = trainingService;
    this.calorieCalculator = calorieCalculator;
    this.recommendationService = recommendationService;
    this.oneRmPredictionService = oneRmPredictionService;
    this.acwrService = acwrService;
    this.churnDetectionService = churnDetectionService;
    this.noticeService = noticeService;
    this.trainerAdviceService = trainerAdviceService;
    this.aiTrainingSuggestionService = aiTrainingSuggestionService;
    this.aiFatigueCommentService = aiFatigueCommentService;
    this.fatigueCalculator = fatigueCalculator;
    this.trainingStatsService = trainingStatsService;
  }

  @GetMapping("/menu")
  public String menu(
      @RequestParam(name = "date", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate selectedDate,
      Model model,
      Principal principal) {
    LocalDate today = LocalDate.now();

    Long userId = trainingService.getUserIdByEmail(principal.getName());
    log.debug("ログインユーザーのIDは: {}", userId);

    // 1. 日付の決定
    if (selectedDate == null) selectedDate = today;
    User userEntity = trainingService.getUserByEmail(principal.getName());

    // 2. カレンダーの期間（42日分）を計算
    YearMonth yearMonth = YearMonth.from(selectedDate);
    LocalDate firstDay = yearMonth.atDay(1);
    int firstDayValue = firstDay.getDayOfWeek().getValue();
    LocalDate calendarStart = firstDay.minusDays((long) firstDayValue - 1);
    LocalDate calendarEnd = calendarStart.plusDays(41);

    List<LocalDate> dateList = new ArrayList<>();
    for (int i = 0; i < 42; i++) {
      dateList.add(calendarStart.plusDays(i));
    }

    // 3. カレンダー期間内のデータを一括取得
    List<Training> allTrainings = trainingDao.selectByDate(userId, calendarStart, calendarEnd);

    // 4. 日付ごとにMapへ分類
    Map<LocalDate, List<Training>> trainingMap =
        allTrainings.stream().collect(Collectors.groupingBy(Training::getTrainingDate));

    // 5. 表示する日のデータをMapから取得
    List<Training> trainingList = trainingMap.getOrDefault(selectedDate, new ArrayList<>());

    // マスターデータの一括取得
    List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

    for (Training t : trainingList) {
      t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
      t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
    }

    // 6. カレンダーのステータス判定
    List<String> dayStatusList = new ArrayList<>();
    for (LocalDate date : dateList) {
      List<Training> dailyTrainings = trainingMap.getOrDefault(date, Collections.emptyList());
      if (dailyTrainings.isEmpty()) {
        dayStatusList.add("NONE");
      } else {
        boolean allDone = dailyTrainings.stream().allMatch(Training::isAllCompleted);
        dayStatusList.add(allDone ? "COMPLETED" : "IN_PROGRESS");
      }
    }

    // ita4-4 (A) 追加対応: 未読のトレーナーアドバイスがある日付をカレンダー上でハイライトする
    // （/notices・/detailのいずれかで閲覧するとハイライトのみ消える。本文自体は履歴として残る）
    Set<String> unreadAdviceDates =
        trainerAdviceService.getActiveForUser(userId).stream()
            .filter(a -> a.getReadAt() == null)
            .map(a -> a.getTargetDate().toString())
            .collect(Collectors.toSet());

    // 7. 集計とModelセット
    long totalCount = trainingList.size();
    long completedCount = trainingList.stream().filter(Training::isAllCompleted).count();

    // ita2-3: 消費カロリー表示（menu.html拡大分）
    Map<String, com.example.traning.entity.TrainingItemMaster> itemMasterByNameForCalorie =
        trainingMasterDao.selectAllItems().stream()
            .collect(
                Collectors.toMap(
                    com.example.traning.entity.TrainingItemMaster::getItemName,
                    item -> item,
                    (a, b) -> a));
    CalorieCalculator.CalorieEstimate menuCalorieEstimate =
        calorieCalculator.estimate(trainingList, itemMasterByNameForCalorie);

    // ita7-1 2-2: /detail画面（過去分編集画面）の閲覧内容をmenu.htmlの詳細モーダルへ統合するため、
    // 合計ボリューム・所要時間・トレーニングコース・トレーナーアドバイスをここで算出する
    // （旧trainingDetail()メソッドと同一ロジック。/detail自体は廃止）。
    long totalVolumeKg = 0;
    for (Training t : trainingList) {
      if (t.getDetails() == null) continue;
      for (TrainingDetail d : t.getDetails()) {
        if (SetType.fromValueOrMain(d.getSetType()) == SetType.WARMUP) continue;
        if (d.getWeight() != null && d.getReps() != null) {
          totalVolumeKg += Math.round(d.getWeight() * d.getReps());
        }
      }
    }
    String duration =
        trainingList.stream()
            .map(Training::getDuration)
            .filter(d -> d != null && !d.isEmpty() && !d.equals("00:00:00"))
            .findFirst()
            .orElse("00:00:00");
    List<String> trainingCourse =
        trainingList.stream()
            .map(Training::getMenu)
            .filter(m -> m != null && !m.isEmpty())
            .collect(Collectors.toList());
    List<TrainerAdvice> advicesForSelectedDate =
        trainerAdviceService.getActiveForUserAndDate(userId, selectedDate);
    List<AdviceItem> adviceItems =
        advicesForSelectedDate.stream()
            .map(a -> new AdviceItem(a.getBody(), a.getReadAt() == null))
            .toList();
    trainerAdviceService.markAsRead(advicesForSelectedDate);
    model.addAttribute("totalVolume", totalVolumeKg);
    model.addAttribute("duration", duration);
    model.addAttribute("trainingCourse", trainingCourse);
    model.addAttribute("advices", adviceItems);

    // 疲労マップ用データ（過去7日間の半減期モデル）。FatigueCalculatorへ委譲する
    // （旧実装はここに独自のインライン計算を持っており、ita5-3のWARMUP除外がこの画面だけ
    // 反映されていなかったため、共通実装に統一した）。
    FatigueCalculator.FatigueResult fatigueResult = fatigueCalculator.calculate(userId, today);
    Map<String, Long> volumeByPart = fatigueResult.volumeByPart();
    Map<String, Integer> setsByPart = fatigueResult.setsByPart();
    Map<String, Integer> fatiguePct = fatigueResult.fatiguePct();

    Map<String, String> partNameMap =
        partList.stream()
            .collect(
                Collectors.toMap(
                    TrainingMaster::getPartCode, TrainingMaster::getPartName, (a, b) -> a));

    // Thymeleaf のMap変数キーアクセス問題を避けるため、List<Map>で渡す
    List<Map<String, Object>> fatigueRows = new ArrayList<>();
    for (String p : FatigueCalculator.PART_ORDER) {
      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("partCode", p);
      row.put("partName", partNameMap.getOrDefault(p, p));
      row.put("volume", volumeByPart.getOrDefault(p, 0L));
      row.put("sets", setsByPart.getOrDefault(p, 0));
      row.put("pct", fatiguePct.getOrDefault(p, 0));
      fatigueRows.add(row);
    }

    // R1〜R3・今日の予定: 統計バー算出ロジックはita7-2でモバイル(MobileStatsController)と共用するため
    // TrainingStatsServiceへ切り出し済み。Web側の出力（モデル属性の型・値）は切り出し前と完全に同一。
    TrainingStatsService.TrainingStats stats = trainingStatsService.getStats(userId, today);
    int monthlyCount = stats.monthlyCount();
    List<Map<String, Object>> weekParts = stats.weekPartsAsMapList();
    String volumeChangeText = stats.volumeChangeText();
    boolean volumeChangePositive = stats.volumeChangePositive();
    WeeklyProgram todayProgram = stats.todayProgram();
    String todayPartLabel = stats.todayPartLabel();

    model.addAttribute("loginUser", userEntity);
    model.addAttribute("targetMonth", yearMonth);
    model.addAttribute("dateList", dateList);
    model.addAttribute("today", today);
    model.addAttribute("selectedDate", selectedDate);
    model.addAttribute(
        "selectedDateStr", selectedDate.toString()); // Add formatted string for comparison
    model.addAttribute("trainingList", trainingList);
    model.addAttribute("prevMonth", yearMonth.minusMonths(1).atDay(1));
    model.addAttribute("nextMonth", yearMonth.plusMonths(1).atDay(1));
    model.addAttribute("partList", partList);
    model.addAttribute("totalCount", totalCount);
    model.addAttribute("completedCount", completedCount);
    model.addAttribute("isDailyCompleted", totalCount > 0 && totalCount == completedCount);
    model.addAttribute("dayStatusList", dayStatusList);
    model.addAttribute("unreadAdviceDates", unreadAdviceDates);
    model.addAttribute("fatiguePct", fatiguePct);
    model.addAttribute("fatigueRows", fatigueRows);
    model.addAttribute("monthlyCount", monthlyCount);
    model.addAttribute("weekParts", weekParts);
    model.addAttribute("volumeChangeText", volumeChangeText);
    model.addAttribute("volumeChangePositive", volumeChangePositive);
    model.addAttribute("todayProgram", todayProgram);
    model.addAttribute("todayPartLabel", todayPartLabel);
    model.addAttribute("calorieEstimate", menuCalorieEstimate);

    // F3 Phase1: 今日のおすすめメニュー（ルールベース推奨）
    DailyRecommendation dailyRecommendation = recommendationService.getTodayRecommendation(userId);
    Map<String, Double> estimatedOneRmByItem = new LinkedHashMap<>();
    for (RecommendedItem item : dailyRecommendation.items()) {
      estimatedOneRmByItem.put(
          item.itemName(), oneRmPredictionService.estimateOneRm(item.weightMax(), item.repsMin()));
    }
    model.addAttribute("dailyRecommendation", dailyRecommendation);
    model.addAttribute("currentGoalMode", userEntity.getCurrentGoalMode());
    model.addAttribute("estimatedOneRmByItem", estimatedOneRmByItem);

    // F3 Phase2: ACWR警告・離脱検知メッセージ
    Double acwrValue = acwrService.calculate(userId, today);
    model.addAttribute("acwrValue", acwrValue);
    model.addAttribute("acwrWarning", acwrService.isWarning(acwrValue));
    model.addAttribute(
        "churnMessage", churnDetectionService.checkChurnMessage(userId, today).orElse(null));

    // ita2-5: ジム・店舗からのお知らせバナー
    model.addAttribute("activeNoticeCount", noticeService.getActiveForUser(userEntity).size());

    // ita5-1 機能1: AIトレーニング提案（同意済みユーザーのみ、週頭に1回7日分をまとめて生成・キャッシュし、本日分のみ表示）
    boolean isViewingToday = selectedDate.isEqual(today);
    model.addAttribute("isViewingToday", isViewingToday);
    model.addAttribute("aiAdviceConsent", Boolean.TRUE.equals(userEntity.getAiAdviceConsent()));
    if (isViewingToday) {
      model.addAttribute(
          "aiTrainingSuggestion",
          aiTrainingSuggestionService.getTodayEntry(userEntity).orElse(null));
    }

    // ita5-1 機能3: 筋肉疲労度マップのAI分析（種目登録のたびではなく、その日のトレーニングが
    // 完了したタイミングで1日1回だけ生成する。確定済み設計）
    boolean isTodayFullyCompleted =
        isViewingToday
            && !trainingList.isEmpty()
            && trainingList.stream().allMatch(Training::isAllCompleted);
    if (isTodayFullyCompleted) {
      model.addAttribute(
          "aiFatigueComment",
          aiFatigueCommentService
              .getOrGenerateTodayComment(userEntity, fatigueResult)
              .orElse(null));
    }

    return "menu";
  }

  @GetMapping("/api/training-items")
  @ResponseBody
  public List<TrainingItemMaster> getItems(
      @RequestParam String partCode,
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate date) {
    // date が本日以降の場合のみ、使用可能（master_flg=1）な種目に絞り込む。
    // date未指定・過去日付の場合は従来通り全件返す（過去記録の閲覧・編集に影響させないため）。
    if (date != null && !date.isBefore(LocalDate.now())) {
      return trainingMasterDao.selectActiveItemsByPart(partCode);
    }
    return trainingMasterDao.selectItemsByPart(partCode);
  }

  @GetMapping("/api/training/{id}")
  @ResponseBody
  public ResponseEntity<Training> getTraining(@PathVariable Long id, Principal principal) {
    Training training = trainingService.getTrainingById(id);

    // 存在しない場合
    if (training == null) {
      log.warn("存在しないトレーニングへのアクセス: ID={}", id);
      return ResponseEntity.notFound().build();
    }

    // ★ 所有者チェック: ログインユーザーのデータでなければ 403 を返す
    Long currentUserId = trainingService.getUserIdByEmail(principal.getName());
    if (!training.getUserId().equals(currentUserId)) {
      log.warn("不正アクセス検知: ユーザー {} がトレーニング {} へアクセスしようとしました", currentUserId, id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.ok(training);
  }

  @GetMapping("/api/training-parts")
  @ResponseBody
  public List<TrainingMaster> getTrainingParts() {
    return trainingMasterDao.selectAllParts();
  }

  @GetMapping("/api/growth-chart")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getGrowthChart(
      @RequestParam String itemName,
      @RequestParam(defaultValue = "3m") String period,
      Principal principal) {

    if (!Set.of("1m", "3m", "6m", "1y").contains(period)) {
      return ResponseEntity.badRequest().body(Map.of("error", "不正なperiod値です"));
    }
    if (itemName == null || itemName.isBlank() || itemName.length() > 50) {
      return ResponseEntity.badRequest().body(Map.of("error", "不正なitemName値です"));
    }

    Long userId = trainingService.getUserIdByEmail(principal.getName());
    Map<String, Object> chartData = trainingService.getGrowthChartData(userId, itemName, period);

    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chartData);
  }

  @GetMapping("/api/previous-training")
  @ResponseBody
  public ResponseEntity<PreviousTrainingResponse> getPreviousTraining(
      @RequestParam String itemName, Principal principal) {

    if (itemName == null || itemName.isBlank() || itemName.length() > 50) {
      return ResponseEntity.badRequest().build();
    }

    Long userId = trainingService.getUserIdByEmail(principal.getName());
    PreviousTrainingResponse response = trainingService.getPreviousTraining(userId, itemName);

    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
  }

  @GetMapping("/api/training-items-grouped")
  @ResponseBody
  public Map<String, List<TrainingItemMaster>> getTrainingItemsGrouped(
      @RequestParam(required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate date) {
    List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
    Map<String, List<TrainingItemMaster>> groupedItems = new java.util.HashMap<>();
    // date が本日以降の場合のみ、使用可能（master_flg=1）な種目に絞り込む（/api/training-items と同じ方針）。
    boolean activeOnly = date != null && !date.isBefore(LocalDate.now());

    for (TrainingMaster part : parts) {
      List<TrainingItemMaster> items =
          activeOnly
              ? trainingMasterDao.selectActiveItemsByPart(part.getPartCode())
              : trainingMasterDao.selectItemsByPart(part.getPartCode());
      groupedItems.put(part.getPartCode(), items);
    }

    return groupedItems;
  }

  @GetMapping("/api/training/by-date")
  @ResponseBody
  public ResponseEntity<List<Map<String, Object>>> getTrainingsByDate(
      @RequestParam
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate date,
      Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<Training> trainings = trainingDao.selectByUserIdAndDate(userId.intValue(), date);
    List<Map<String, Object>> result =
        trainings.stream()
            .map(
                t -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", t.getId());
                  m.put("menu", t.getMenu());
                  m.put("partCode", t.getPartCode());
                  m.put("displayOrder", t.getDisplayOrder());
                  return m;
                })
            .toList();
    return ResponseEntity.ok(result);
  }

  /** menu.html詳細モーダル表示用のトレーナーアドバイス投影（未読なら{@code unread=true}）。 */
  public record AdviceItem(String body, boolean unread) {}
}
