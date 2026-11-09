package com.example.traning.smarttrainer.autogen;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.organization.Organization;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.periodization.PeriodizationViews.CycleDetail;
import com.example.traning.periodization.PeriodizationViews.DayView;
import com.example.traning.periodization.PeriodizationViews.ItemView;
import com.example.traning.periodization.PeriodizationViews.TodayAssignment;
import com.example.traning.periodization.PeriodizationViews.WeekView;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.dao.PersonalRecordDao;
import com.example.traning.restpreference.RestPreferenceService;
import com.example.traning.restpreference.UserItemRestPreference;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.EquipmentView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.ExclusionView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.FormView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerateCommand;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GeneratedAlternative;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GeneratedItem;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerationResult;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.Mode;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.PartOption;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.ProgramDayView;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.Candidate;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PartRequest;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlanInput;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlanResult;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlannedItem;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.WeightSource;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.WeightSuggestion;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.smarttrainer.recommendation.RecommendationStrategy;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * メニュー自動作成（機能見直し-1-#4）。データを集めて {@link WorkoutPlanner} に渡し、結果に定型文の理由を付ける。生成結果は保存しない。
 *
 * <p>モード（QA Q4-5・Q4-6）: 部位指定が無ければプログラムの当日の部位・強度%、部位指定があれば部位はユーザー優先で強度%のみプログラム。
 */
@Service
public class WorkoutGenerationService {

  static final String NO_RECORD_REASON = "記録が無いため重量は空欄です。目標回数をあと3〜4回はできる余裕を残して終えられる重量で始めてください";
  static final String FATIGUE_WARNING = "この部位は疲労が残っています。重量・セット数を調整してください";

  private final PeriodizationService periodizationService;
  private final FatigueCalculator fatigueCalculator;
  private final OneRmPredictionService oneRmPredictionService;
  private final EquipmentDao equipmentDao;
  private final UserExcludedItemDao userExcludedItemDao;
  private final AutogenHistoryDao autogenHistoryDao;
  private final TrainingMasterDao trainingMasterDao;
  private final TrainingDao trainingDao;
  private final PersonalRecordDao personalRecordDao;
  private final UserDao userDao;
  private final RestPreferenceService restPreferenceService;
  private final Map<GoalMode, RecommendationStrategy> strategies;

  public WorkoutGenerationService(
      PeriodizationService periodizationService,
      FatigueCalculator fatigueCalculator,
      OneRmPredictionService oneRmPredictionService,
      EquipmentDao equipmentDao,
      UserExcludedItemDao userExcludedItemDao,
      AutogenHistoryDao autogenHistoryDao,
      TrainingMasterDao trainingMasterDao,
      TrainingDao trainingDao,
      PersonalRecordDao personalRecordDao,
      UserDao userDao,
      RestPreferenceService restPreferenceService,
      List<RecommendationStrategy> strategyList) {
    this.periodizationService = periodizationService;
    this.fatigueCalculator = fatigueCalculator;
    this.oneRmPredictionService = oneRmPredictionService;
    this.equipmentDao = equipmentDao;
    this.userExcludedItemDao = userExcludedItemDao;
    this.autogenHistoryDao = autogenHistoryDao;
    this.trainingMasterDao = trainingMasterDao;
    this.trainingDao = trainingDao;
    this.personalRecordDao = personalRecordDao;
    this.userDao = userDao;
    this.restPreferenceService = restPreferenceService;
    this.strategies = new EnumMap<>(GoalMode.class);
    for (RecommendationStrategy s : strategyList) {
      this.strategies.put(s.getMode(), s);
    }
  }

  /** 対象日のプログラムの枠（内部用）。itemNames はその日の枠に登録された種目。 */
  record ProgramDay(
      String cycleName,
      int weekNumber,
      int totalWeeks,
      BigDecimal intensityPct,
      String partCode,
      List<String> itemNames) {}

  // ===================== 条件入力画面 =====================

  @Transactional
  public FormView getForm(Long userId, LocalDate date) {
    LocalDate target = resolveDate(date);
    Map<String, String> partNames = partNames(userId);
    Map<String, String> itemParts = activeItemParts();
    return new FormView(
        AutogenRules.MIN_AVAILABLE_MINUTES,
        partNames.entrySet().stream().map(e -> new PartOption(e.getKey(), e.getValue())).toList(),
        visibleEquipment(userId).stream()
            .map(e -> new EquipmentView(e.getId(), e.getName(), e.getCategory()))
            .toList(),
        listExclusions(userId, partNames, itemParts),
        resolveProgramDay(userId, target).map(p -> toView(p, partNames)).orElse(null));
  }

  // ===================== 生成 =====================

  @Transactional
  public GenerationResult generate(Long userId, GenerateCommand cmd) {
    LocalDate date = resolveDate(cmd.date());
    int minutes = validateMinutes(cmd.availableMinutes());
    Map<String, String> partNames = partNames(userId);
    List<String> desiredParts = validateParts(cmd.desiredPartCodes(), partNames);
    List<Equipment> equipment = visibleEquipment(userId);
    Set<Long> availableEquipment = validateEquipment(cmd.availableEquipmentIds(), equipment);

    User user = userDao.selectById(userId.intValue());
    GoalMode goal = GoalMode.fromString(user != null ? user.getCurrentGoalMode() : null);
    AutogenRules.IntensityReps goalIr = AutogenRules.GOAL_INTENSITY_REPS.get(goal);
    int baseSets = strategies.get(goal).getSets();

    Optional<ProgramDay> program = resolveProgramDay(userId, date);
    // プログラムの休養日に部位指定なしの場合も、おまかせで通常どおり組み強度は今週のプログラムの%を使う（QA Q4-10）
    BigDecimal programPct = program.map(ProgramDay::intensityPct).orElse(null);
    double intensity = programPct != null ? programPct.doubleValue() / 100.0 : goalIr.intensity();
    int reps =
        programPct != null
            ? WorkoutPlanner.programReps(
                goalIr.reps(), oneRmPredictionService.maxRepsAtIntensity(intensity))
            : goalIr.reps();

    // モード判定と対象部位
    Map<String, Integer> fatigue = fatigueCalculator.calculate(userId, date).fatiguePct();
    Set<String> recentParts =
        new HashSet<>(
            autogenHistoryDao.selectPartsWithMainSets(
                userId, date.minusDays(AutogenRules.RECENT_MAIN_SET_DAYS), date));
    Mode mode;
    List<String> parts;
    boolean specified;
    if (!desiredParts.isEmpty()) {
      mode = program.isPresent() ? Mode.USER_OVERRIDE : Mode.ADHOC;
      parts = desiredParts;
      specified = true;
    } else if (program.isPresent() && program.get().partCode() != null) {
      mode = Mode.CYCLE_COMPLEMENT;
      parts = List.of(program.get().partCode());
      specified = true;
    } else {
      mode = Mode.ADHOC;
      parts = omakaseParts(partNames.keySet());
      specified = false;
    }
    int setReduction = 0;
    boolean allFatigued = false;
    if (!specified) {
      List<String> rested =
          parts.stream().filter(p -> !isFatigued(fatigue, recentParts, p)).toList();
      if (rested.isEmpty()) {
        allFatigued = true;
        setReduction = AutogenRules.ALL_FATIGUED_SET_REDUCTION;
      } else {
        parts = rested;
      }
    }

    // 候補
    List<UserExcludedItem> savedExclusions = userExcludedItemDao.selectByUserId(userId);
    Set<String> excluded = new HashSet<>();
    savedExclusions.forEach(e -> excluded.add(e.getItemName()));
    if (cmd.excludeItemNames() != null) excluded.addAll(cmd.excludeItemNames());
    CandidateFactory factory =
        new CandidateFactory(userId, date, equipment, availableEquipment, excluded, program);

    List<PartRequest> requests = new ArrayList<>();
    for (String p : parts) {
      List<Candidate> candidates = factory.candidatesFor(p);
      requests.add(
          new PartRequest(
              p,
              specified,
              isFatigued(fatigue, recentParts, p),
              candidates,
              factory.programItemCount(candidates)));
    }
    PlanResult plan =
        WorkoutPlanner.plan(
            new PlanInput(minutes, requests, intensity, reps, baseSets, setReduction));

    ReasonContext ctx = new ReasonContext(mode, specified, allFatigued, programPct, partNames);
    List<GeneratedItem> items = new ArrayList<>();
    for (PlannedItem pi : plan.items()) {
      Candidate c = pi.candidate();
      items.add(
          new GeneratedItem(
              c.itemName(),
              c.partCode(),
              partNames.getOrDefault(c.partCode(), c.partCode()),
              c.compound(),
              c.bodyweight(),
              pi.sets(),
              reps,
              pi.weight().weightKg(),
              pi.restSeconds(),
              buildReason(ctx, c, pi.weight()),
              specified && pi.part().fatigued() ? FATIGUE_WARNING : null,
              pi.alternatives().stream()
                  .map(
                      a ->
                          new GeneratedAlternative(
                              a.candidate().itemName(),
                              a.candidate().partCode(),
                              a.candidate().compound(),
                              a.candidate().bodyweight(),
                              reps,
                              a.weight().weightKg(),
                              a.restSeconds(),
                              buildReason(ctx, a.candidate(), a.weight())))
                  .toList()));
    }

    LocalDateTime now = LocalDateTime.now();
    Map<String, String> itemParts = activeItemParts();
    List<ExclusionView> due =
        savedExclusions.stream()
            .filter(e -> isConfirmationDue(e, now))
            .map(e -> toView(e, partNames, itemParts, now))
            .toList();

    return new GenerationResult(
        plan.status().name(),
        mode,
        program.map(p -> toView(p, partNames)).orElse(null),
        Math.round(intensity * 1000) / 10.0,
        minutes,
        (int) Math.ceil(plan.estimatedSeconds() / 60.0),
        items,
        due);
  }

  /** おまかせの部位: 大きい部位→小さい部位→体幹の順。 */
  static List<String> omakaseParts(Set<String> partCodes) {
    return partCodes.stream().sorted(Comparator.comparingInt(AutogenRules::partSizeRank)).toList();
  }

  /** 高疲労: 疲労度がしきい値を超える、または前日・当日にその部位の本番セットがある（疲労度は「最近やった割合」のため、頻繁に行う部位では値が薄まる）。 */
  static boolean isFatigued(
      Map<String, Integer> fatiguePct, Set<String> recentMainSetParts, String partCode) {
    return fatiguePct.getOrDefault(partCode, 0) > AutogenRules.HIGH_FATIGUE_THRESHOLD_PCT
        || recentMainSetParts.contains(partCode);
  }

  // ===================== 理由の定型文（QA Q4-3） =====================

  record ReasonContext(
      Mode mode,
      boolean specified,
      boolean allFatigued,
      BigDecimal programPct,
      Map<String, String> partNames) {}

  static String buildReason(ReasonContext ctx, Candidate c, WeightSuggestion weight) {
    if (weight.source() == WeightSource.NONE) return NO_RECORD_REASON;
    String part = ctx.partNames().getOrDefault(c.partCode(), c.partCode());
    String pct = ctx.programPct() != null ? formatPct(ctx.programPct()) : null;
    String base;
    if (ctx.mode() == Mode.CYCLE_COMPLEMENT) {
      base = "プログラムの" + part + "の日。目標強度 " + pct + "%で計算しています";
    } else if (ctx.mode() == Mode.USER_OVERRIDE) {
      base = part + "を指定。強度はプログラムの " + pct + "%で計算しています";
    } else if (ctx.specified()) {
      base = part + "を指定。" + (c.compound() ? "複合種目を先にしています" : "単関節種目は後にしています");
    } else if (ctx.allFatigued()) {
      base = "全部位に疲労が残っているため、セット数を1つ減らしています";
    } else if (pct != null) {
      base = "疲労の少ない部位から選び、強度はプログラムの " + pct + "%で計算しています";
    } else {
      base = "疲労の少ない部位から選んでいます";
    }
    if (weight.source() == WeightSource.LAST_MAIN_SET) {
      return base + "。推定1RMが無いため前回の重量を入れています";
    }
    if (weight.source() == WeightSource.BODYWEIGHT) {
      return base + "。自重種目のため回数のみです";
    }
    return base;
  }

  private static String formatPct(BigDecimal pct) {
    return pct.stripTrailingZeros().toPlainString();
  }

  // ===================== 候補の作成 =====================

  /** 部位ごとの候補（除外・器具の条件に合う種目）を、選びやすい順に並べて作る。 */
  private final class CandidateFactory {
    private final Map<String, List<TrainingItemMaster>> itemsByPart;
    private final Map<String, List<Equipment>> linksByItem;
    private final Set<Long> availableEquipment;
    private final Set<String> excluded;
    private final Map<String, Double> oneRms = new HashMap<>();
    private final Map<String, Double> lastWeights = new HashMap<>();
    private final Map<String, Integer> restOverrides = new HashMap<>();
    private final Map<String, Long> recentCounts = new HashMap<>();
    private final List<String> programItems;

    CandidateFactory(
        Long userId,
        LocalDate date,
        List<Equipment> equipment,
        Set<Long> availableEquipment,
        Set<String> excluded,
        Optional<ProgramDay> program) {
      this.availableEquipment = availableEquipment;
      this.excluded = excluded;
      this.programItems = program.map(ProgramDay::itemNames).orElse(List.of());
      this.itemsByPart =
          trainingMasterDao.selectActiveItems().stream()
              .collect(
                  Collectors.groupingBy(
                      TrainingItemMaster::getPartCode, LinkedHashMap::new, Collectors.toList()));
      Map<Long, Equipment> equipmentById = new HashMap<>();
      equipment.forEach(e -> equipmentById.put(e.getId(), e));
      this.linksByItem = new HashMap<>();
      for (TrainingItemEquipment link : equipmentDao.selectAllItemEquipment()) {
        Equipment e = equipmentById.get(link.getEquipmentId());
        // 他ジムの器具が必要な種目は、このユーザーには使えない器具として扱う
        linksByItem
            .computeIfAbsent(link.getItemName(), k -> new ArrayList<>())
            .add(e != null ? e : unavailableEquipment(link.getEquipmentId()));
      }
      for (PersonalRecord pr : personalRecordDao.selectByUserId(userId)) {
        if (pr.getMaxWeight() != null && pr.getMaxReps() != null && pr.getMaxWeight() > 0) {
          oneRms.put(
              pr.getItemName(),
              oneRmPredictionService.estimateOneRm(pr.getMaxWeight(), pr.getMaxReps()));
        }
      }
      for (LastMainSetWeight w : autogenHistoryDao.selectLastMainSetWeights(userId, date)) {
        lastWeights.put(w.getItemName(), w.getWeight());
      }
      for (UserItemRestPreference p : restPreferenceService.listByUserId(userId)) {
        restOverrides.put(p.getItemName(), p.getRestSeconds());
      }
      LocalDate windowStart = date.minusDays(AutogenRules.RECENT_ITEM_WINDOW_DAYS);
      for (Training t :
          trainingDao.selectByUserIdAndDateRange(userId.intValue(), windowStart, date)) {
        if (t.getMenu() != null) recentCounts.merge(t.getMenu(), 1L, Long::sum);
      }
    }

    List<Candidate> candidatesFor(String partCode) {
      return itemsByPart.getOrDefault(partCode, List.of()).stream()
          .filter(i -> !excluded.contains(i.getItemName()))
          .filter(i -> equipmentAvailable(i.getItemName()))
          .sorted(
              Comparator.comparingInt((TrainingItemMaster i) -> programRank(i.getItemName()))
                  .thenComparing(
                      i -> recentCounts.getOrDefault(i.getItemName(), 0L),
                      Comparator.reverseOrder())
                  .thenComparing(
                      i -> i.getDisplayOrder() != null ? i.getDisplayOrder() : Integer.MAX_VALUE))
          .map(this::toCandidate)
          .toList();
    }

    /** 候補の先頭に並んだプログラムの当日の種目の件数。 */
    int programItemCount(List<Candidate> candidates) {
      return (int) candidates.stream().filter(c -> programItems.contains(c.itemName())).count();
    }

    private int programRank(String itemName) {
      int idx = programItems.indexOf(itemName);
      return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    /** 紐付けの無い種目は器具で除外しない（QA Q4-1: データが空の間の安全側）。器具の指定が無ければ絞り込まない。 */
    private boolean equipmentAvailable(String itemName) {
      List<Equipment> links = linksByItem.get(itemName);
      if (links == null || links.isEmpty() || availableEquipment == null) return true;
      return links.stream().allMatch(e -> availableEquipment.contains(e.getId()));
    }

    private Candidate toCandidate(TrainingItemMaster i) {
      List<Equipment> links = linksByItem.getOrDefault(i.getItemName(), List.of());
      boolean bodyweight =
          !links.isEmpty()
              && links.stream()
                  .allMatch(e -> AutogenRules.BODYWEIGHT_CATEGORY.equals(e.getCategory()));
      double increment =
          links.stream()
              .map(Equipment::getWeightIncrementKg)
              .filter(v -> v != null && v.signum() > 0)
              .mapToDouble(BigDecimal::doubleValue)
              .max()
              .orElse(AutogenRules.DEFAULT_WEIGHT_INCREMENT_KG);
      return new Candidate(
          i.getItemName(),
          i.getPartCode(),
          !Boolean.FALSE.equals(i.getIsCompound()),
          bodyweight,
          increment,
          oneRms.get(i.getItemName()),
          lastWeights.get(i.getItemName()),
          restOverrides.get(i.getItemName()));
    }
  }

  private static Equipment unavailableEquipment(Long id) {
    Equipment e = new Equipment();
    e.setId(-id);
    return e;
  }

  // ===================== 除外 =====================

  @Transactional(readOnly = true)
  public List<ExclusionView> listExclusions(Long userId) {
    return listExclusions(userId, partNames(userId), activeItemParts());
  }

  private List<ExclusionView> listExclusions(
      Long userId, Map<String, String> partNames, Map<String, String> itemParts) {
    LocalDateTime now = LocalDateTime.now();
    return userExcludedItemDao.selectByUserId(userId).stream()
        .map(e -> toView(e, partNames, itemParts, now))
        .toList();
  }

  /** 除外を登録する。最初の再確認は登録から2週間後。 */
  @Transactional
  public ExclusionView addExclusion(Long userId, String itemName, String reason) {
    String name = itemName == null ? "" : itemName.trim();
    Map<String, String> itemParts = activeItemParts();
    if (!itemParts.containsKey(name)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "種目が見つかりません");
    }
    if (userExcludedItemDao.selectByUserIdAndItemName(userId, name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "すでに除外している種目です");
    }
    String trimmedReason = reason == null || reason.isBlank() ? null : reason.trim();
    if (trimmedReason != null
        && trimmedReason.length() > AutogenRules.EXCLUSION_REASON_MAX_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "理由は" + AutogenRules.EXCLUSION_REASON_MAX_LENGTH + "文字以内で入力してください");
    }
    LocalDateTime now = LocalDateTime.now();
    UserExcludedItem e = new UserExcludedItem();
    e.setUserId(userId);
    e.setItemName(name);
    e.setReason(trimmedReason);
    e.setCreatedAt(now);
    e.setLastConfirmedAt(now);
    e.setNextConfirmationDueAt(firstConfirmationDueAt(now));
    userExcludedItemDao.insert(e);
    return toView(e, partNames(userId), itemParts, now);
  }

  @Transactional
  public void removeExclusion(Long userId, String itemName) {
    UserExcludedItem e = requireExclusion(userId, itemName);
    userExcludedItemDao.delete(e);
  }

  /** 再確認の回答。「まだ必要」なら次の再確認を4週間後に、「解除する」なら削除する（自動解除はしない）。 */
  @Transactional
  public void confirmExclusion(Long userId, String itemName, boolean keep) {
    UserExcludedItem e = requireExclusion(userId, itemName);
    if (!keep) {
      userExcludedItemDao.delete(e);
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    e.setLastConfirmedAt(now);
    e.setNextConfirmationDueAt(nextConfirmationDueAt(now));
    userExcludedItemDao.update(e);
  }

  static LocalDateTime firstConfirmationDueAt(LocalDateTime createdAt) {
    return createdAt.plusDays(AutogenRules.EXCLUSION_FIRST_CONFIRM_DAYS);
  }

  static LocalDateTime nextConfirmationDueAt(LocalDateTime confirmedAt) {
    return confirmedAt.plusDays(AutogenRules.EXCLUSION_REPEAT_CONFIRM_DAYS);
  }

  static boolean isConfirmationDue(UserExcludedItem e, LocalDateTime now) {
    return e.getNextConfirmationDueAt() != null && !e.getNextConfirmationDueAt().isAfter(now);
  }

  private UserExcludedItem requireExclusion(Long userId, String itemName) {
    return userExcludedItemDao
        .selectByUserIdAndItemName(userId, itemName)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "除外している種目が見つかりません"));
  }

  private static ExclusionView toView(
      UserExcludedItem e,
      Map<String, String> partNames,
      Map<String, String> itemParts,
      LocalDateTime now) {
    String partCode = itemParts.get(e.getItemName());
    return new ExclusionView(
        e.getItemName(),
        partCode,
        partCode != null ? partNames.getOrDefault(partCode, partCode) : null,
        e.getReason(),
        e.getCreatedAt() != null ? e.getCreatedAt().toLocalDate() : null,
        e.getNextConfirmationDueAt(),
        isConfirmationDue(e, now));
  }

  // ===================== 期分けプログラム（#3）連携 =====================

  /** 今日は getTodayAssignment（サイクル終了の処理を含む）、未来日はサイクルの全体像から読み取りのみで求める。 */
  Optional<ProgramDay> resolveProgramDay(Long userId, LocalDate date) {
    if (date.equals(LocalDate.now())) {
      TodayAssignment a = periodizationService.getTodayAssignment(userId, date);
      if (!a.hasActiveCycle() || a.weekNumber() == null) return Optional.empty();
      DayView day = a.dayTemplate();
      return Optional.of(
          new ProgramDay(
              a.cycleName(),
              a.weekNumber(),
              a.totalWeeks(),
              a.targetIntensityPct(),
              day != null ? day.partCode() : null,
              itemNames(day)));
    }
    Optional<CycleDetail> detail = periodizationService.getActiveCycleDetail(userId, date);
    if (detail.isEmpty()) return Optional.empty();
    CycleDetail c = detail.get();
    long days = ChronoUnit.DAYS.between(c.startDate(), date);
    int week = days < 0 ? 1 : (int) (days / 7) + 1;
    if (week > c.totalWeeks()) return Optional.empty();
    String dayCode = PeriodizationService.toDayCode(date.getDayOfWeek());
    Optional<WeekView> wv = c.weeks().stream().filter(w -> w.weekNumber() == week).findFirst();
    DayView day =
        wv.flatMap(w -> w.days().stream().filter(d -> dayCode.equals(d.dayOfWeek())).findFirst())
            .orElse(null);
    return Optional.of(
        new ProgramDay(
            c.name(),
            week,
            c.totalWeeks(),
            wv.map(WeekView::targetIntensityPct).orElse(null),
            day != null ? day.partCode() : null,
            itemNames(day)));
  }

  private static List<String> itemNames(DayView day) {
    if (day == null || day.items() == null) return List.of();
    return day.items().stream()
        .sorted(Comparator.comparingInt(ItemView::displayOrder))
        .map(ItemView::itemName)
        .toList();
  }

  private static ProgramDayView toView(ProgramDay p, Map<String, String> partNames) {
    return new ProgramDayView(
        p.cycleName(),
        p.weekNumber(),
        p.totalWeeks(),
        p.intensityPct() != null ? p.intensityPct().doubleValue() : null,
        p.partCode(),
        p.partCode() != null ? partNames.getOrDefault(p.partCode(), p.partCode()) : null);
  }

  // ===================== 入力チェック・マスタ =====================

  private static LocalDate resolveDate(LocalDate date) {
    LocalDate today = LocalDate.now();
    if (date == null) return today;
    if (date.isBefore(today)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "過去の日付ではメニューを作成できません");
    }
    return date;
  }

  static int validateMinutes(Integer minutes) {
    if (minutes == null
        || minutes < AutogenRules.MIN_AVAILABLE_MINUTES
        || minutes > AutogenRules.MAX_AVAILABLE_MINUTES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "使える時間は"
              + AutogenRules.MIN_AVAILABLE_MINUTES
              + "〜"
              + AutogenRules.MAX_AVAILABLE_MINUTES
              + "分で指定してください");
    }
    return minutes;
  }

  /** 指定順を保ったまま重複を除く。部位マスタに無い部位・対象外の部位（カーディオ）は400。 */
  static List<String> validateParts(List<String> codes, Map<String, String> partNames) {
    if (codes == null) return List.of();
    Set<String> result = new LinkedHashSet<>();
    for (String code : codes) {
      if (code == null || !partNames.containsKey(code)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部位の指定が正しくありません");
      }
      result.add(code);
    }
    return new ArrayList<>(result);
  }

  static Set<Long> validateEquipment(List<Long> ids, List<Equipment> visible) {
    if (ids == null) return null;
    Set<Long> visibleIds = visible.stream().map(Equipment::getId).collect(Collectors.toSet());
    for (Long id : ids) {
      if (id == null || !visibleIds.contains(id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "器具の指定が正しくありません");
      }
    }
    return new HashSet<>(ids);
  }

  /** 生成の対象にする部位（部位マスタの表示順、全ジム共通と所属ジムの部位、カーディオは除く）→ 部位名。 */
  private Map<String, String> partNames(Long userId) {
    Set<Long> orgs = new HashSet<>(visibleOrganizationIds(userId));
    Map<String, String> names = new LinkedHashMap<>();
    for (TrainingMaster m : trainingMasterDao.selectAllParts()) {
      if (m.getPartCode() == null || AutogenRules.NON_TARGET_PARTS.contains(m.getPartCode())) {
        continue;
      }
      if (m.getOrganizationId() != null && !orgs.contains(m.getOrganizationId())) continue;
      names.putIfAbsent(m.getPartCode(), m.getPartName());
    }
    return names;
  }

  private Map<String, String> activeItemParts() {
    Map<String, String> map = new HashMap<>();
    for (TrainingItemMaster i : trainingMasterDao.selectActiveItems()) {
      map.putIfAbsent(i.getItemName(), i.getPartCode());
    }
    return map;
  }

  private List<Equipment> visibleEquipment(Long userId) {
    return equipmentDao.selectByOrganizationIds(visibleOrganizationIds(userId));
  }

  private List<Long> visibleOrganizationIds(Long userId) {
    List<Long> ids = new ArrayList<>();
    ids.add(Organization.ALL_ORGANIZATION_ID);
    Long orgId = userDao.selectOrganizationIdById(userId);
    if (orgId != null && orgId != Organization.ALL_ORGANIZATION_ID) ids.add(orgId);
    return ids;
  }
}
