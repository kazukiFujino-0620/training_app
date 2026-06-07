package com.example.traning.training.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dto.PreviousTrainingResponse;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;
import com.example.traning.weekly.WeeklyProgram;
import com.example.traning.weekly.WeeklyProgramService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final WeeklyProgramService weeklyProgramService;

  private static final Map<String, String> PART_LABEL_MAP =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  public MenuController(
      TrainingDao trainingDao,
      TrainingDetailDao trainingDetailDao,
      TrainingMasterDao trainingMasterDao,
      TrainingService trainingService,
      CalorieCalculator calorieCalculator,
      WeeklyProgramService weeklyProgramService) {
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.trainingMasterDao = trainingMasterDao;
    this.trainingService = trainingService;
    this.calorieCalculator = calorieCalculator;
    this.weeklyProgramService = weeklyProgramService;
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

    // 7. 集計とModelセット
    long totalCount = trainingList.size();
    long completedCount = trainingList.stream().filter(Training::isAllCompleted).count();

    // 疲労マップ用データ（過去7日間の半減期モデル）
    LocalDate fatigueStart = today.minusDays(6);
    List<Training> fatigueTrainings =
        trainingDao.selectByUserIdAndDateRange(userId.intValue(), fatigueStart, today);

    String[] partOrder = {"CHEST", "BACK", "SHOULDER", "ARM", "LEG"};
    Map<String, Long> volumeByPart = new java.util.LinkedHashMap<>();
    Map<String, Integer> setsByPart = new java.util.LinkedHashMap<>();
    Map<String, Double> rawFatigueByPart = new java.util.LinkedHashMap<>();
    for (String p : partOrder) {
      volumeByPart.put(p, 0L);
      setsByPart.put(p, 0);
      rawFatigueByPart.put(p, 0.0);
    }
    for (Training ft : fatigueTrainings) {
      String pc = ft.getPartCode();
      if (pc == null || !volumeByPart.containsKey(pc)) continue;
      List<TrainingDetail> fDetails = trainingDetailDao.selectByTrainingId(ft.getId());
      long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(ft.getTrainingDate(), today);
      double decay = Math.pow(0.5, daysAgo / 2.0); // 48時間で疲労50%回復
      long vol = 0;
      int completedSets = 0;
      for (TrainingDetail fd : fDetails) {
        if (!fd.getIsCompleted()) continue;
        if (fd.getWeight() != null && fd.getReps() != null) {
          vol += Math.round(fd.getWeight() * fd.getReps());
        }
        completedSets++;
      }
      volumeByPart.merge(pc, vol, Long::sum);
      setsByPart.merge(pc, completedSets, Integer::sum);
      rawFatigueByPart.merge(pc, vol * decay, Double::sum);
    }
    // 各部位の生ボリューム（decay なし）で正規化 → トレーニング日=100%、日々回復
    Map<String, Integer> fatiguePct = new java.util.LinkedHashMap<>();
    for (String p : partOrder) {
      long rawVol = volumeByPart.get(p);
      double decayed = rawFatigueByPart.get(p);
      int pct = rawVol > 0 ? (int) Math.round(decayed / rawVol * 100) : 0;
      fatiguePct.put(p, pct);
    }
    Map<String, String> partNameMap =
        partList.stream()
            .collect(
                Collectors.toMap(
                    TrainingMaster::getPartCode, TrainingMaster::getPartName, (a, b) -> a));

    log.info(
        "疲労マップ: {}件取得, partCodes={}",
        fatigueTrainings.size(),
        fatigueTrainings.stream()
            .map(Training::getPartCode)
            .distinct()
            .collect(Collectors.toList()));
    log.info("疲労集計: volume={}, sets={}, pct={}", volumeByPart, setsByPart, fatiguePct);

    // Thymeleaf のMap変数キーアクセス問題を避けるため、List<Map>で渡す
    List<Map<String, Object>> fatigueRows = new ArrayList<>();
    for (String p : partOrder) {
      Map<String, Object> row = new java.util.LinkedHashMap<>();
      row.put("partCode", p);
      row.put("partName", partNameMap.getOrDefault(p, p));
      row.put("volume", volumeByPart.getOrDefault(p, 0L));
      row.put("sets", setsByPart.getOrDefault(p, 0));
      row.put("pct", fatiguePct.getOrDefault(p, 0));
      fatigueRows.add(row);
    }

    // R1: 今月のトレーニング回数
    int monthlyCount =
        trainingDao.countByUserIdAndMonth(userId, today.getYear(), today.getMonthValue());

    // R2: 今週（月曜起点）の部位カバレッジ
    LocalDate weekStart =
        today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    java.util.Set<String> weekPartsDoneSet =
        new java.util.HashSet<>(
            trainingDao.selectDistinctPartsByUserIdAndDateRange(userId, weekStart, today));
    String[][] partDefs = {
      {"CHEST", "胸"}, {"BACK", "背中"}, {"SHOULDER", "肩"}, {"ARM", "腕"}, {"LEG", "脚"}
    };
    List<Map<String, Object>> weekParts = new ArrayList<>();
    for (String[] pd : partDefs) {
      Map<String, Object> pi = new java.util.LinkedHashMap<>();
      pi.put("name", pd[1]);
      pi.put("done", weekPartsDoneSet.contains(pd[0]));
      weekParts.add(pi);
    }

    // R3: 前週比ボリューム
    LocalDate prevWeekStart = weekStart.minusWeeks(1);
    LocalDate prevWeekEnd = weekStart.minusDays(1);
    Double thisWeekVolume =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, weekStart, today);
    Double prevWeekVolume =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, prevWeekStart, prevWeekEnd);
    String volumeChangeText;
    boolean volumeChangePositive = true;
    if (prevWeekVolume == null || prevWeekVolume == 0.0) {
      volumeChangeText = "前週データなし";
    } else {
      double thisVol = thisWeekVolume != null ? thisWeekVolume : 0.0;
      int pctChange = (int) Math.round((thisVol - prevWeekVolume) / prevWeekVolume * 100);
      volumeChangePositive = pctChange >= 0;
      volumeChangeText = pctChange >= 0 ? "+" + pctChange + "%" : pctChange + "%";
    }

    // 週間プログラム: 今日の予定
    WeeklyProgram todayProgram = weeklyProgramService.getTodayProgram(userId).orElse(null);
    String todayPartLabel =
        todayProgram != null ? PART_LABEL_MAP.getOrDefault(todayProgram.getPartCode(), "") : null;

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
    model.addAttribute("fatiguePct", fatiguePct);
    model.addAttribute("fatigueRows", fatigueRows);
    model.addAttribute("monthlyCount", monthlyCount);
    model.addAttribute("weekParts", weekParts);
    model.addAttribute("volumeChangeText", volumeChangeText);
    model.addAttribute("volumeChangePositive", volumeChangePositive);
    model.addAttribute("todayProgram", todayProgram);
    model.addAttribute("todayPartLabel", todayPartLabel);
    model.addAttribute("showReorderMenu", true);

    return "menu";
  }

  @AuditLog(action = "TRAINING_SAVE", targetTable = "trainings")
  @PostMapping("/menu/save")
  public String save(@ModelAttribute Training training, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    training.setUserId(userId);

    if (training.getId() == null) {
      training.setCreateDatetime(LocalDateTime.now());
    }
    training.setUpdatedDatetime(LocalDateTime.now());

    trainingService.save(training, principal);

    return "redirect:/menu?date=" + training.getTrainingDate();
  }

  @AuditLog(action = "TRAINING_DELETE", targetTable = "trainings")
  @PostMapping("/menu/delete")
  public String delete(@RequestParam("id") Long id, Principal principal) {
    log.info("削除リクエストが来ました！ ID: {}", id);

    // ★ 所有者チェック: 自分のトレーニングでなければ拒否
    Training training = trainingService.getTrainingById(id);
    if (training == null) {
      log.warn("削除対象が存在しません ID: {}", id);
      return "redirect:/menu";
    }

    Long currentUserId = trainingService.getUserIdByEmail(principal.getName());
    if (!training.getUserId().equals(currentUserId)) {
      log.warn("不正な削除リクエスト: ユーザー {} がトレーニング {} を削除しようとしました", currentUserId, id);
      return "redirect:/menu";
    }

    LocalDate date = training.getTrainingDate();
    trainingService.deleteTraining(id);

    return "redirect:/menu?date=" + date;
  }

  @GetMapping("/api/training-items")
  @ResponseBody
  public List<TrainingItemMaster> getItems(@RequestParam String partCode) {
    return trainingMasterDao.selectItemsByPart(partCode);
  }

  @GetMapping("/start/training")
  public String startTraining(
      @RequestParam("date")
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate selectedDate,
      Model model,
      Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<Training> trainingList = trainingService.getFullTrainingData(userId, selectedDate);
    List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

    // タイマーの復元用に最初の training の duration を取得
    String restoredDuration = "00:00:00";
    if (!trainingList.isEmpty() && trainingList.get(0).getDuration() != null) {
      restoredDuration = trainingList.get(0).getDuration();
    }

    model.addAttribute("selectedDate", selectedDate);
    model.addAttribute("currentUserId", userId);
    model.addAttribute("trainingList", trainingList);
    model.addAttribute("partList", partList);
    model.addAttribute("restoredDuration", restoredDuration);
    model.addAttribute("showReorderMenu", true);

    return "training/start_training";
  }

  @AuditLog(action = "TRAINING_SAVE", targetTable = "trainings")
  @PostMapping("/api/training/save")
  @ResponseBody
  public Long apiSaveTraining(@Valid @RequestBody Training training, Principal principal) {
    training.setUserId(trainingService.getUserIdByEmail(principal.getName()));

    if (training.getCreateDatetime() == null) {
      training.setCreateDatetime(LocalDateTime.now());
    }
    training.setUpdatedDatetime(LocalDateTime.now());

    trainingService.save(training, principal);

    return training.getId();
  }

  @AuditLog(action = "TRAINING_FINISH", targetTable = "trainings")
  @PostMapping("/api/training/finish")
  @ResponseBody
  public ResponseEntity<String> finishTrainig(
      @Valid @RequestBody List<Training> trainingList, Principal principal) {
    try {
      // ★ IDOR 対策: ログインユーザーが所有するトレーニングのみ保存を許可
      Long currentUserId = trainingService.getUserIdByEmail(principal.getName());

      for (Training t : trainingList) {
        // 1. セットデータ確認
        if (t.getDetails() == null || t.getDetails().isEmpty()) {
          return ResponseEntity.badRequest().body("セットデータが空の種目があります。");
        }

        // 2. トレーニングが実在するか確認
        if (t.getId() == null || t.getId() <= 0) {
          log.warn("不正なトレーニングID: {}", t.getId());
          return ResponseEntity.badRequest().body("不正なトレーニングIDです。");
        }

        Training existingTraining = trainingService.getTrainingById(t.getId());
        if (existingTraining == null) {
          log.warn("トレーニングが見つかりません: ID={}", t.getId());
          return ResponseEntity.notFound().build();
        }

        // 3. 所有者確認: 自分のトレーニングでなければ拒否
        if (!existingTraining.getUserId().equals(currentUserId)) {
          log.warn("不正なアクセス検知: ユーザー {} がトレーニング {} を保存しようとしました", currentUserId, t.getId());
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body("このトレーニングを変更する権限がありません。");
        }
      }

      trainingService.saveAll(trainingList);
      return ResponseEntity.ok("保存に成功しました");
    } catch (Exception e) {
      log.error("トレーニング保存エラー", e);
      return ResponseEntity.internalServerError().body("登録に失敗しました。時間をおいて再度お試しください。");
    }
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

  @AuditLog(action = "TRAINING_UPDATE", targetTable = "trainings")
  @PostMapping("/api/training/update/{id}")
  @ResponseBody
  public ResponseEntity<Void> updateTraining(
      @PathVariable Long id, @Valid @RequestBody Training training, Principal principal) {
    Training existingTraining = trainingService.getTrainingById(id);
    if (existingTraining == null) {
      return ResponseEntity.notFound().build();
    }

    training.setId(id);
    training.setUserId(existingTraining.getUserId());
    training.setTrainingDate(existingTraining.getTrainingDate());
    training.setCreateDatetime(existingTraining.getCreateDatetime());

    Long currentUserId = trainingService.getUserIdByEmail(principal.getName());
    if (!training.getUserId().equals(currentUserId)) {
      log.warn("不正な更新リクエスト: ユーザー {} がトレーニング {} を更新しようとしました", currentUserId, id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    trainingService.save(training, principal);
    return ResponseEntity.ok().build();
  }

  @AuditLog(action = "TRAINING_DELETE", targetTable = "trainings")
  @PostMapping("/api/training/delete/{id}")
  @ResponseBody
  public ResponseEntity<Void> deleteTraining(@PathVariable Long id, Principal principal) {
    Training existingTraining = trainingService.getTrainingById(id);
    if (existingTraining == null) {
      return ResponseEntity.notFound().build();
    }

    Long currentUserId = trainingService.getUserIdByEmail(principal.getName());
    if (!existingTraining.getUserId().equals(currentUserId)) {
      log.warn("不正な削除リクエスト: ユーザー {} がトレーニング {} を削除しようとしました", currentUserId, id);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    trainingService.deleteTraining(id);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/training/register")
  public String trainingRegister(
      @RequestParam(name = "date", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate selectedDate,
      Model model,
      Principal principal) {
    LocalDate today = LocalDate.now();
    if (selectedDate == null) selectedDate = today;
    Long userId = trainingService.getUserIdByEmail(principal.getName());

    List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

    // 同日の既存トレーニングを取得（画面初期表示用）
    // Thymeleaf の JS インライン展開で使うため Map のネスト構造で渡す
    List<Training> existingList = trainingDao.selectByUserIdAndDate(userId.intValue(), selectedDate);
    for (Training t : existingList) {
      t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
      t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
    }
    List<Map<String, Object>> existingTrainings = existingList.stream()
        .map(t -> {
          Map<String, Object> tm = new LinkedHashMap<>();
          tm.put("id", t.getId());
          tm.put("menu", t.getMenu());
          tm.put("partCode", t.getPartCode());
          tm.put("partName", t.getPartName() != null ? t.getPartName() : "");
          tm.put("memo", t.getMemo() != null ? t.getMemo() : "");
          List<Map<String, Object>> details = t.getDetails() == null
              ? new ArrayList<>()
              : t.getDetails().stream().map(d -> {
                  Map<String, Object> dm = new LinkedHashMap<>();
                  dm.put("setNumber", d.getSetNumber());
                  dm.put("weight", d.getWeight() != null ? d.getWeight() : 0.0);
                  dm.put("reps", d.getReps() != null ? d.getReps() : 0);
                  dm.put("isCompleted", d.getIsCompleted());
                  dm.put("setType", d.getSetType() != null ? d.getSetType() : "MAIN");
                  return dm;
                }).toList();
          tm.put("details", details);
          return tm;
        }).toList();

    model.addAttribute("selectedDate", selectedDate);
    model.addAttribute("userId", userId);
    model.addAttribute("partList", partList);
    model.addAttribute("showReorderMenu", true);
    model.addAttribute("existingTrainings", existingTrainings);

    return "training/training-register";
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

  @PostMapping("/api/training/superset/group")
  @ResponseBody
  public ResponseEntity<Map<String, Long>> groupSuperset(
      @RequestBody Map<String, List<Long>> body, Principal principal) {
    List<Long> trainingIds = body.get("trainingIds");
    if (trainingIds == null || trainingIds.size() != 2) {
      return ResponseEntity.badRequest().build();
    }
    try {
      Long userId = trainingService.getUserIdByEmail(principal.getName());
      Long groupId = trainingService.groupSuperset(trainingIds, userId);
      return ResponseEntity.ok(Map.of("supersetGroupId", groupId));
    } catch (IllegalArgumentException e) {
      log.warn("スーパーセットグループ化失敗: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/api/training/superset/ungroup")
  @ResponseBody
  public ResponseEntity<Void> ungroupSuperset(
      @RequestBody Map<String, Long> body, Principal principal) {
    Long supersetGroupId = body.get("supersetGroupId");
    if (supersetGroupId == null) {
      return ResponseEntity.badRequest().build();
    }
    try {
      Long userId = trainingService.getUserIdByEmail(principal.getName());
      trainingService.ungroupSuperset(supersetGroupId, userId);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      log.warn("スーパーセット解除失敗: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
  }

  @GetMapping("/api/training/superset/candidates")
  @ResponseBody
  public ResponseEntity<List<Map<String, Object>>> getSupersetCandidates(
      @RequestParam
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate date,
      Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<Training> candidates = trainingService.getCandidatesForSuperset(userId, date);
    List<Map<String, Object>> result =
        candidates.stream()
            .map(
                t -> {
                  Map<String, Object> m = new java.util.LinkedHashMap<>();
                  m.put("trainingId", t.getId());
                  m.put("menu", t.getMenu());
                  m.put("partName", trainingMasterDao.selectNameByCode(t.getPartCode()));
                  return m;
                })
            .collect(Collectors.toList());
    return ResponseEntity.ok(result);
  }

  @GetMapping("/api/training-items-grouped")
  @ResponseBody
  public Map<String, List<TrainingItemMaster>> getTrainingItemsGrouped() {
    List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
    Map<String, List<TrainingItemMaster>> groupedItems = new java.util.HashMap<>();

    for (TrainingMaster part : parts) {
      List<TrainingItemMaster> items = trainingMasterDao.selectItemsByPart(part.getPartCode());
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

  @PostMapping("/api/training/reorder")
  @ResponseBody
  public ResponseEntity<Void> reorderTrainings(
      @RequestBody List<Long> orderedIds, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    for (int i = 0; i < orderedIds.size(); i++) {
      Long id = orderedIds.get(i);
      Training t = trainingService.getTrainingById(id);
      if (t == null || !t.getUserId().equals(userId)) {
        log.warn("不正な順序変更リクエスト: ユーザー {} がトレーニング {} を操作しようとしました", userId, id);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
      trainingDao.updateDisplayOrder(id, i, LocalDateTime.now());
    }
    return ResponseEntity.ok().build();
  }

  @AuditLog(action = "TRAINING_BULK", targetTable = "trainings")
  @PostMapping("/api/training/register-bulk")
  @ResponseBody
  public ResponseEntity<String> registerBulkTraining(
      @RequestBody Map<String, Object> data, Principal principal) {
    try {
      String dateStr = (String) data.get("date");
      if (dateStr == null || dateStr.trim().isEmpty()) {
        return ResponseEntity.badRequest().body("日付が指定されていません。");
      }
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> trainingsData = (List<Map<String, Object>>) data.get("trainings");
      if (trainingsData == null || trainingsData.isEmpty()) {
        return ResponseEntity.badRequest().body("トレーニングデータが指定されていません。");
      }

      LocalDate trainingDate = LocalDate.parse(dateStr);
      Long userId = trainingService.getUserIdByEmail(principal.getName());

      for (int arrayIdx = 0; arrayIdx < trainingsData.size(); arrayIdx++) {
        Map<String, Object> trainingMap = trainingsData.get(arrayIdx);
        Training training;

        if (trainingMap.get("id") != null && !trainingMap.get("id").toString().isEmpty()) {
          Long id = Long.valueOf(trainingMap.get("id").toString());
          Training existingTraining = trainingService.getTrainingById(id);

          if (existingTraining != null) {
            // IDOR 防止: 取得したレコードがログインユーザーのものか確認する
            if (!existingTraining.getUserId().equals(userId)) {
              log.warn("不正な一括登録リクエスト: ユーザー {} がトレーニング {} を更新しようとしました", userId, id);
              return ResponseEntity.status(HttpStatus.FORBIDDEN).body("このトレーニングを変更する権限がありません。");
            }
            training = existingTraining;
          } else {
            training = new Training();
            training.setUserId(userId);
            training.setCreateDatetime(LocalDateTime.now());
          }
        } else {
          // id が無い場合は完全に新規登録データとして作成
          training = new Training();
          training.setUserId(userId);
          training.setCreateDatetime(LocalDateTime.now());
        }

        // 画面から変更されうる共通項目を上書き
        training.setTrainingDate(trainingDate);
        training.setMenu((String) trainingMap.get("menu"));
        training.setPartCode((String) trainingMap.get("partCode"));
        training.setUpdatedDatetime(LocalDateTime.now());
        // 配列インデックスを display_order として設定（新規・既存共通）
        training.setDisplayOrder(arrayIdx);

        // 新規登録の時だけ完了フラグを初期化
        if (training.getId() == null) {
          training.setIsCompleted(false);
          training.setIsAllCompleted(false);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailsData =
            (List<Map<String, Object>>) trainingMap.get("details");
        List<TrainingDetail> details = new ArrayList<>();

        for (int i = 0; i < detailsData.size(); i++) {
          Map<String, Object> detailMap = detailsData.get(i);
          TrainingDetail detail = new TrainingDetail();
          Object weightObj = detailMap.get("weight");
          if (weightObj != null) {
            detail.setWeight(((Number) weightObj).doubleValue());
          } else {
            detail.setWeight(1.0);
          }

          Object repsObj = detailMap.get("reps");
          if (repsObj != null) {
            detail.setReps(((Number) repsObj).intValue());
          } else {
            detail.setReps(0);
          }

          detail.setSetNumber(i + 1);
          Object completedObj = detailMap.getOrDefault("isCompleted", false);
          detail.setIsCompleted(Boolean.parseBoolean(completedObj.toString()));
          Object setTypeObj = detailMap.get("setType");
          if (setTypeObj instanceof String s
              && (s.equals("WARMUP") || s.equals("MAIN") || s.equals("DROP"))) {
            detail.setSetType(s);
          }
          details.add(detail);
        }

        training.setDetails(details);
        // サービスを呼び出して保存（更新、または登録）
        trainingService.save(training, principal);
      }

      return ResponseEntity.ok("保存に成功しました");
    } catch (Exception e) {
      log.error("一括登録エラー", e);
      return ResponseEntity.internalServerError().body("登録に失敗しました。時間をおいて再度お試しください。");
    }
  }

  @GetMapping("/detail")
  public String trainingDetail(
      @RequestParam(name = "date", required = false)
          @org.springframework.format.annotation.DateTimeFormat(
              iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
          LocalDate date,
      Model model,
      Principal principal) {

    LocalDate today = LocalDate.now();
    if (date == null) date = today;

    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<Training> trainings = trainingDao.selectByUserIdAndDate(userId.intValue(), date);

    // 部位コード → 部位名のマップを構築
    List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
    Map<String, String> partNameMap =
        parts.stream()
            .collect(
                Collectors.toMap(
                    TrainingMaster::getPartCode, TrainingMaster::getPartName, (a, b) -> a));

    // 各トレーニングにセット詳細と部位名をセット、合計ボリュームを集計
    long totalVolumeKg = 0;
    for (Training t : trainings) {
      List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(t.getId());
      t.setDetails(details);
      t.setPartName(partNameMap.getOrDefault(t.getPartCode(), t.getPartCode()));
      for (TrainingDetail d : details) {
        if (d.getWeight() != null && d.getReps() != null) {
          totalVolumeKg += Math.round(d.getWeight() * d.getReps());
        }
      }
    }

    // トレーニング時間: 全種目共通（finish時に同じ値を書き込む）の最初の非ゼロ値を使用
    String duration =
        trainings.stream()
            .map(Training::getDuration)
            .filter(d -> d != null && !d.isEmpty() && !d.equals("00:00:00"))
            .findFirst()
            .orElse("00:00:00");

    int durationMinutes = parseDurationToMinutes(duration);
    User loginUser = trainingService.getUserByEmail(principal.getName());
    CalorieCalculator.CalorieEstimate calorieEstimate =
        calorieCalculator.estimate(loginUser, durationMinutes);

    // トレーニングコース（種目の順序リスト）
    List<String> course =
        trainings.stream()
            .map(Training::getMenu)
            .filter(m -> m != null && !m.isEmpty())
            .collect(Collectors.toList());

    model.addAttribute("loginUser", loginUser);
    model.addAttribute("trainings", trainings);
    model.addAttribute("date", date);
    model.addAttribute("today", today);
    model.addAttribute("totalVolume", totalVolumeKg);
    model.addAttribute("duration", duration);
    model.addAttribute("calorieEstimate", calorieEstimate);
    model.addAttribute("isToday", date.equals(today));
    model.addAttribute("trainingCourse", course);

    return "training/detail";
  }

  private int parseDurationToMinutes(String duration) {
    if (duration == null || duration.isEmpty()) return 0;
    String[] parts = duration.split(":");
    if (parts.length != 3) return 0;
    try {
      return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
