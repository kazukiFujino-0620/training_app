package com.example.traning.periodization;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.organization.Organization;
import com.example.traning.periodization.PeriodizationViews.CustomCycleInput;
import com.example.traning.periodization.PeriodizationViews.CustomCycleResult;
import com.example.traning.periodization.PeriodizationViews.CycleDetail;
import com.example.traning.periodization.PeriodizationViews.DayInput;
import com.example.traning.periodization.PeriodizationViews.DayView;
import com.example.traning.periodization.PeriodizationViews.ItemInput;
import com.example.traning.periodization.PeriodizationViews.ItemStagnation;
import com.example.traning.periodization.PeriodizationViews.ItemView;
import com.example.traning.periodization.PeriodizationViews.PresetSummary;
import com.example.traning.periodization.PeriodizationViews.TodayAssignment;
import com.example.traning.periodization.PeriodizationViews.WeekInput;
import com.example.traning.periodization.PeriodizationViews.WeekView;
import com.example.traning.periodization.StagnationDetectionService.StagnationLevel;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 期分け構造を持つ事前構築プログラム（機能見直し-1-#3 詳細設計書3章）。Web/モバイル共通。 */
@Service
@RequiredArgsConstructor
public class PeriodizationService {

  static final int MAX_ITEMS_PER_DAY = 20;
  static final int MIN_TARGET_SETS = 1;
  static final int MAX_TARGET_SETS = 20;
  private static final int ITEM_NAME_MAX_LENGTH = 100;
  private static final int CYCLE_NAME_MAX_LENGTH = 100;

  /** 目標強度(%1RM)の上限。%1RMの定義上100を超えない。 */
  private static final BigDecimal MAX_INTENSITY_PCT = new BigDecimal("100.0");

  private static final List<String> DAY_CODES =
      List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

  private final ProgramCycleDao programCycleDao;
  private final PresetProgramDao presetProgramDao;
  private final StagnationDetectionService stagnationDetectionService;
  private final PersonalRecordService personalRecordService;
  private final OneRmPredictionService oneRmPredictionService;
  private final TrainingMasterDao trainingMasterDao;
  private final UserDao userDao;

  // ===================== 参照系 =====================

  /** ユーザーが選択できるプリセット一覧（目的カテゴリ・表示順）。 */
  @Transactional(readOnly = true)
  public List<PresetSummary> listPresets(Long userId) {
    return presetProgramDao.selectByOrganizationIds(visibleOrganizationIds(userId)).stream()
        .map(
            p ->
                new PresetSummary(
                    p.getId(),
                    p.getName(),
                    p.getPurposeCategory(),
                    p.getTotalWeeks(),
                    p.getDescription()))
        .toList();
  }

  /** 採用中（ACTIVE）サイクルの全体像。無ければempty。 */
  @Transactional(readOnly = true)
  public Optional<CycleDetail> getActiveCycleDetail(Long userId, LocalDate today) {
    return programCycleDao
        .selectActiveByUserId(userId)
        .map(cycle -> toCycleDetail(cycle, today, userId));
  }

  /**
   * 当日の割当（設計書3-1節）。 total_weeksを超えていたらサイクルをCOMPLETEDにし、次サイクルは自動作成せず cycleCompleted=trueを返す（QA
   * Q3-7）。
   */
  @Transactional
  public TodayAssignment getTodayAssignment(Long userId, LocalDate today) {
    Optional<ProgramCycle> activeOpt = programCycleDao.selectActiveByUserId(userId);
    if (activeOpt.isEmpty()) {
      boolean pending = isRenewalPending(userId);
      return emptyAssignment(pending);
    }
    ProgramCycle cycle = activeOpt.get();
    int weekNumber = weekNumberOf(cycle, today);
    if (weekNumber > cycle.getTotalWeeks()) {
      programCycleDao.completeById(cycle.getId());
      return emptyAssignment(true);
    }

    ProgramCycleWeek week =
        programCycleDao.selectWeeksByCycleId(cycle.getId()).stream()
            .filter(w -> w.getWeekNumber() == weekNumber)
            .findFirst()
            .orElse(null);
    List<ProgramCycleDayTemplate> dayTemplates =
        programCycleDao.selectDayTemplatesByCycleId(cycle.getId());
    Map<Long, List<ProgramCycleDayTemplateItem>> itemsByDay =
        groupItems(programCycleDao.selectItemsByCycleId(cycle.getId()));
    BigDecimal intensity = week != null ? week.getTargetIntensityPct() : null;
    Map<String, Double> oneRmCache = new HashMap<>();

    String todayCode = toDayCode(today.getDayOfWeek());
    DayView todayView =
        dayTemplates.stream()
            .filter(d -> d.getWeekNumber() == weekNumber && todayCode.equals(d.getDayOfWeek()))
            .findFirst()
            .map(d -> toDayView(d, itemsByDay, intensity, userId, oneRmCache))
            .orElse(null);

    // 停滞判定（QA Q3-4 2026-09-24確定）: 判定は記録保存時に済ませて保持しているため、ここでは再判定せず保持結果を表示する。
    // 対象は当日の種目。休養日（当日割当なし）は今週予定の全種目。
    Set<String> targetItems = new LinkedHashSet<>();
    if (todayView != null) {
      todayView.items().forEach(i -> targetItems.add(i.itemName()));
    } else {
      dayTemplates.stream()
          .filter(d -> d.getWeekNumber() == weekNumber)
          .forEach(
              d ->
                  itemsByDay
                      .getOrDefault(d.getId(), List.of())
                      .forEach(i -> targetItems.add(i.getItemName())));
    }
    boolean deloadThisWeek = week != null && Boolean.TRUE.equals(week.getDeload());
    boolean deloadNextWeek =
        programCycleDao.selectWeeksByCycleId(cycle.getId()).stream()
            .anyMatch(
                w -> w.getWeekNumber() == weekNumber + 1 && Boolean.TRUE.equals(w.getDeload()));
    StagnationSummary stagnation =
        summarizeStagnation(
            stagnationDetectionService.getStoredLevels(userId, new ArrayList<>(targetItems)),
            new ArrayList<>(targetItems),
            deloadThisWeek || deloadNextWeek);
    StagnationLevel overall = stagnation.overall();
    List<ItemStagnation> stagnantItems = stagnation.items();

    return new TodayAssignment(
        true,
        false,
        cycle.getId(),
        cycle.getName(),
        weekNumber,
        cycle.getTotalWeeks(),
        intensity,
        week != null && Boolean.TRUE.equals(week.getDeload()),
        todayView,
        overall.name(),
        stagnantItems);
  }

  // ===================== 更新系 =====================

  /**
   * プリセットを採用して新しいACTIVEサイクルを作る（tier=BEGINNER_PRESET）。既存ACTIVEサイクルはARCHIVEDにする。
   *
   * @return 作成したサイクルID
   */
  @Transactional
  public Long adoptPreset(Long userId, Long presetProgramId, LocalDate startDate) {
    PresetProgram preset = findVisiblePreset(userId, presetProgramId);
    return createCycleFromPreset(userId, preset, CycleTier.BEGINNER_PRESET, null, startDate);
  }

  /**
   * 曜日→部位の割当を変更する（設計書3-1節）。該当週・曜日の行が無ければ新規作成する。
   *
   * <p>QA Q3-6見直し（2026-09-23）: プリセットを編集してもtierは変更しない。
   */
  @Transactional
  public void customizeDayTemplate(
      Long userId, Long cycleId, int weekNumber, String dayOfWeek, String partCode) {
    ProgramCycle cycle = requireOwnedActiveCycle(userId, cycleId);
    if (weekNumber < 1 || weekNumber > cycle.getTotalWeeks()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "週番号が不正です");
    }
    String dayCode = normalizeDayCode(dayOfWeek);
    String part = (partCode == null || partCode.isBlank()) ? null : partCode.trim();
    Optional<ProgramCycleDayTemplate> existing =
        programCycleDao.selectDayTemplateByCycleWeekDay(cycleId, weekNumber, dayCode);
    if (existing.isPresent()) {
      programCycleDao.updateDayTemplatePartCode(existing.get().getId(), part);
    } else {
      ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
      d.setCycleId(cycleId);
      d.setWeekNumber(weekNumber);
      d.setDayOfWeek(dayCode);
      d.setPartCode(part);
      programCycleDao.insertDayTemplate(d);
    }
  }

  /**
   * 指定した曜日テンプレートの種目リストを全置換で保存する（QA Q3-3）。
   *
   * <p>QA Q3-6見直し（2026-09-23）: プリセットを編集してもtierは変更しない。
   */
  @Transactional
  public void customizeDayTemplateItems(Long userId, Long dayTemplateId, List<ItemInput> items) {
    ProgramCycleDayTemplate dayTemplate =
        programCycleDao
            .selectDayTemplateById(dayTemplateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "曜日割当が見つかりません"));
    ProgramCycle cycle = requireOwnedActiveCycle(userId, dayTemplate.getCycleId());
    List<ItemInput> validated = validateItems(items);

    programCycleDao.deleteItemsByDayTemplateId(dayTemplateId);
    int order = 1;
    for (ItemInput input : validated) {
      ProgramCycleDayTemplateItem item = new ProgramCycleDayTemplateItem();
      item.setDayTemplateId(dayTemplateId);
      item.setItemName(input.itemName());
      item.setDisplayOrder(order++);
      item.setTargetSets(input.targetSets());
      programCycleDao.insertDayTemplateItem(item);
    }
    // 新たに追加された種目の計画値（成長グラフ）用にbaselineを補完する（既存スナップショットは動かさない）
    snapshotBaselines(
        cycle.getId(),
        userId,
        validated.stream().map(ItemInput::itemName).collect(Collectors.toSet()));
  }

  /**
   * プリセットを使わず白紙から期分けサイクルを組んで開始する（QA Q3-6見直しで新設）。tier=INTERMEDIATE_CUSTOM。 既存ACTIVEサイクルはARCHIVEDにする。
   *
   * @return 作成したサイクルIDと、保存を妨げない補足・注意（CustomCycleRules）
   */
  @Transactional
  public CustomCycleResult createCustomCycle(
      Long userId, CustomCycleInput input, LocalDate startDate) {
    CustomCycleInput valid = validateCustomCycle(input);
    if (startDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "開始日を指定してください");
    }
    programCycleDao
        .selectActiveByUserId(userId)
        .ifPresent(c -> programCycleDao.archiveById(c.getId()));

    ProgramCycle cycle = new ProgramCycle();
    cycle.setUserId(userId);
    cycle.setName(valid.name());
    cycle.setTotalWeeks(valid.totalWeeks());
    cycle.setStartDate(startDate);
    cycle.setTier(CycleTier.INTERMEDIATE_CUSTOM.name());
    cycle.setStatus(CycleStatus.ACTIVE.name());
    programCycleDao.insert(cycle);
    Long cycleId = cycle.getId();

    for (WeekInput wi : valid.weeks()) {
      ProgramCycleWeek w = new ProgramCycleWeek();
      w.setCycleId(cycleId);
      w.setWeekNumber(wi.weekNumber());
      w.setTargetIntensityPct(wi.targetIntensityPct());
      w.setDeload(wi.deload());
      programCycleDao.insertWeek(w);
    }
    Set<String> itemNames = new LinkedHashSet<>();
    for (DayInput di : valid.days()) {
      ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
      d.setCycleId(cycleId);
      d.setWeekNumber(di.weekNumber());
      d.setDayOfWeek(di.dayOfWeek());
      d.setPartCode(di.partCode());
      programCycleDao.insertDayTemplate(d);
      int order = 1;
      for (ItemInput ii : di.items()) {
        ProgramCycleDayTemplateItem item = new ProgramCycleDayTemplateItem();
        item.setDayTemplateId(d.getId());
        item.setItemName(ii.itemName());
        item.setDisplayOrder(order++);
        item.setTargetSets(ii.targetSets());
        programCycleDao.insertDayTemplateItem(item);
        itemNames.add(ii.itemName());
      }
    }
    snapshotBaselines(cycleId, userId, itemNames);
    List<Boolean> deloadByWeek =
        valid.weeks().stream()
            .sorted(java.util.Comparator.comparing(WeekInput::weekNumber))
            .map(WeekInput::deload)
            .toList();
    return new CustomCycleResult(cycleId, CustomCycleRules.evaluate(deloadByWeek));
  }

  /**
   * サイクル終了後の選択（QA Q3-7）。直近サイクルがCOMPLETEDの状態でのみ受け付ける。
   *
   * @return 新しく作成したサイクルID。GO_FREEFORMの場合はempty
   */
  @Transactional
  public Optional<Long> renewCycle(Long userId, RenewChoice choice, Long newPresetProgramId) {
    if (choice == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "選択肢を指定してください");
    }
    if (programCycleDao.selectActiveByUserId(userId).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "実施中のサイクルがあります");
    }
    ProgramCycle last =
        latestCycle(userId)
            .filter(PeriodizationService::isAwaitingRenewal)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "次の行き先を選ぶサイクルがありません"));
    // QA Q3-7追記（USER確定B）: 3択のいずれを選んでも選択日時を記録する（statusはCOMPLETEDのまま）。
    // これにより選択後は3択画面が再表示されない（GO_FREEFORMでも同様）。
    programCycleDao.markRenewDecidedById(last.getId(), java.time.LocalDateTime.now());
    LocalDate today = LocalDate.now();
    return switch (choice) {
      case REPEAT_SAME -> Optional.of(copyCycle(userId, last, today));
      case CHOOSE_NEW_PRESET -> {
        if (newPresetProgramId == null) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "プログラムを選択してください");
        }
        yield Optional.of(adoptPreset(userId, newPresetProgramId, today));
      }
      case GO_FREEFORM -> Optional.empty();
    };
  }

  // ===================== 内部処理（TrainerPeriodizationServiceからも利用） =====================

  /** プリセットの週・曜日・種目構成をコピーして新しいACTIVEサイクルを作る。既存ACTIVEサイクルはARCHIVEDにする。 */
  @Transactional
  Long createCycleFromPreset(
      Long userId, PresetProgram preset, CycleTier tier, Long trainerId, LocalDate startDate) {
    if (startDate == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "開始日を指定してください");
    }
    programCycleDao
        .selectActiveByUserId(userId)
        .ifPresent(c -> programCycleDao.archiveById(c.getId()));

    ProgramCycle cycle = new ProgramCycle();
    cycle.setUserId(userId);
    cycle.setName(preset.getName());
    cycle.setTotalWeeks(preset.getTotalWeeks());
    cycle.setStartDate(startDate);
    cycle.setTier(tier.name());
    cycle.setSourcePresetId(preset.getId());
    cycle.setCreatedByTrainerId(trainerId);
    cycle.setStatus(CycleStatus.ACTIVE.name());
    programCycleDao.insert(cycle);
    Long cycleId = cycle.getId();

    for (PresetProgramWeek pw : presetProgramDao.selectWeeksByPresetId(preset.getId())) {
      ProgramCycleWeek w = new ProgramCycleWeek();
      w.setCycleId(cycleId);
      w.setWeekNumber(pw.getWeekNumber());
      w.setTargetIntensityPct(pw.getTargetIntensityPct());
      w.setDeload(Boolean.TRUE.equals(pw.getDeload()));
      programCycleDao.insertWeek(w);
    }

    Map<Long, List<PresetProgramDayTemplateItem>> presetItems =
        presetProgramDao.selectItemsByPresetId(preset.getId()).stream()
            .collect(Collectors.groupingBy(PresetProgramDayTemplateItem::getDayTemplateId));
    Set<String> itemNames = new LinkedHashSet<>();
    for (PresetProgramDayTemplate pd :
        presetProgramDao.selectDayTemplatesByPresetId(preset.getId())) {
      ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
      d.setCycleId(cycleId);
      d.setWeekNumber(pd.getWeekNumber());
      d.setDayOfWeek(pd.getDayOfWeek());
      d.setPartCode(pd.getPartCode());
      programCycleDao.insertDayTemplate(d);
      for (PresetProgramDayTemplateItem pi : presetItems.getOrDefault(pd.getId(), List.of())) {
        ProgramCycleDayTemplateItem item = new ProgramCycleDayTemplateItem();
        item.setDayTemplateId(d.getId());
        item.setItemName(pi.getItemName());
        item.setDisplayOrder(pi.getDisplayOrder());
        item.setTargetSets(pi.getTargetSets());
        programCycleDao.insertDayTemplateItem(item);
        itemNames.add(pi.getItemName());
      }
    }
    snapshotBaselines(cycleId, userId, itemNames);
    return cycleId;
  }

  /**
   * 直前のサイクルの内容（週構成・週ごとの強度・ディロード週・曜日ごとの部位と種目。ユーザーの編集結果を含む）を そのままコピーして新しいACTIVEサイクルを作る（REPEAT_SAME、QA
   * 2026-09-23 USER確定A）。tier・採用元プリセット・ 作成トレーナーは引き継ぐ。成長グラフの計画値の基準1RMは、設計書3-1節の「採用/作成時点のスナップショット」に従い
   * 新サイクル開始時点のPRで取り直す。
   */
  private Long copyCycle(Long userId, ProgramCycle source, LocalDate startDate) {
    ProgramCycle cycle = new ProgramCycle();
    cycle.setUserId(userId);
    cycle.setName(source.getName());
    cycle.setTotalWeeks(source.getTotalWeeks());
    cycle.setStartDate(startDate);
    cycle.setTier(source.getTier());
    cycle.setSourcePresetId(source.getSourcePresetId());
    cycle.setCreatedByTrainerId(source.getCreatedByTrainerId());
    cycle.setStatus(CycleStatus.ACTIVE.name());
    programCycleDao.insert(cycle);
    Long cycleId = cycle.getId();

    for (ProgramCycleWeek sw : programCycleDao.selectWeeksByCycleId(source.getId())) {
      ProgramCycleWeek w = new ProgramCycleWeek();
      w.setCycleId(cycleId);
      w.setWeekNumber(sw.getWeekNumber());
      w.setTargetIntensityPct(sw.getTargetIntensityPct());
      w.setDeload(Boolean.TRUE.equals(sw.getDeload()));
      programCycleDao.insertWeek(w);
    }
    Map<Long, List<ProgramCycleDayTemplateItem>> sourceItems =
        groupItems(programCycleDao.selectItemsByCycleId(source.getId()));
    Set<String> itemNames = new LinkedHashSet<>();
    for (ProgramCycleDayTemplate sd : programCycleDao.selectDayTemplatesByCycleId(source.getId())) {
      ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
      d.setCycleId(cycleId);
      d.setWeekNumber(sd.getWeekNumber());
      d.setDayOfWeek(sd.getDayOfWeek());
      d.setPartCode(sd.getPartCode());
      programCycleDao.insertDayTemplate(d);
      for (ProgramCycleDayTemplateItem si : sourceItems.getOrDefault(sd.getId(), List.of())) {
        ProgramCycleDayTemplateItem item = new ProgramCycleDayTemplateItem();
        item.setDayTemplateId(d.getId());
        item.setItemName(si.getItemName());
        item.setDisplayOrder(si.getDisplayOrder());
        item.setTargetSets(si.getTargetSets());
        programCycleDao.insertDayTemplateItem(item);
        itemNames.add(si.getItemName());
      }
    }
    snapshotBaselines(cycleId, userId, itemNames);
    return cycleId;
  }

  /** ユーザーに表示してよいプリセットを取得する。範囲外・存在しない場合は404。 */
  PresetProgram findVisiblePreset(Long userId, Long presetProgramId) {
    if (presetProgramId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "プログラムを選択してください");
    }
    List<Long> visible = visibleOrganizationIds(userId);
    return presetProgramDao
        .selectById(presetProgramId)
        .filter(p -> visible.contains(p.getOrganizationId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "プログラムが見つかりません"));
  }

  /**
   * プリセットの参照範囲となる組織ID（USER確定 2026-09-23、案B）: 0=全組織共通と、ユーザー自身のorganization_id（店舗）のみ。
   * 親組織（GYM）単位の共有は行わず、他店舗のプリセットは表示・採用ともに不可。
   */
  List<Long> visibleOrganizationIds(Long userId) {
    Set<Long> ids = new LinkedHashSet<>();
    ids.add(Organization.ALL_ORGANIZATION_ID);
    Long orgId = userDao.selectOrganizationIdById(userId);
    if (orgId != null) {
      ids.add(orgId);
    }
    return new ArrayList<>(ids);
  }

  /** サイクルに含まれる種目について、現時点のPRから推定1RMをスナップショット保存する（QA Q3-8）。 PR未登録の種目・既に保存済みの種目はスキップする。 */
  private void snapshotBaselines(Long cycleId, Long userId, Set<String> itemNames) {
    Set<String> existing =
        programCycleDao.selectBaselinesByCycleId(cycleId).stream()
            .map(ProgramCycleItemBaseline::getItemName)
            .collect(Collectors.toSet());
    for (String itemName : itemNames) {
      if (existing.contains(itemName)) continue;
      Double oneRm = estimateCurrentOneRm(userId, itemName);
      if (oneRm == null) continue;
      ProgramCycleItemBaseline b = new ProgramCycleItemBaseline();
      b.setCycleId(cycleId);
      b.setItemName(itemName);
      b.setBaselineOneRm(BigDecimal.valueOf(oneRm).setScale(1, RoundingMode.HALF_UP));
      programCycleDao.insertItemBaseline(b);
    }
  }

  /** 現在のPRからの推定1RM（Epley式）。PR未登録ならnull。 */
  private Double estimateCurrentOneRm(Long userId, String itemName) {
    return personalRecordService
        .getByUserIdAndItem(userId, itemName)
        .filter(pr -> pr.getMaxWeight() != null && pr.getMaxReps() != null)
        .map(pr -> oneRmPredictionService.estimateOneRm(pr.getMaxWeight(), pr.getMaxReps()))
        .orElse(null);
  }

  private ProgramCycle requireOwnedActiveCycle(Long userId, Long cycleId) {
    ProgramCycle cycle =
        programCycleDao
            .selectById(cycleId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "サイクルが見つかりません"));
    if (!cycle.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このサイクルを編集する権限がありません");
    }
    if (!CycleStatus.ACTIVE.name().equals(cycle.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "実施中のサイクルのみ編集できます");
    }
    return cycle;
  }

  private CustomCycleInput validateCustomCycle(CustomCycleInput in) {
    if (in == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "プログラム内容を指定してください");
    }
    String name = in.name() == null ? "" : in.name().trim();
    if (name.isEmpty() || name.length() > CYCLE_NAME_MAX_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "プログラム名は1〜" + CYCLE_NAME_MAX_LENGTH + "文字で入力してください");
    }
    Integer totalWeeks = in.totalWeeks();
    if (totalWeeks == null
        || totalWeeks < CustomCycleRules.MIN_TOTAL_WEEKS
        || totalWeeks > CustomCycleRules.MAX_TOTAL_WEEKS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "週数は"
              + CustomCycleRules.MIN_TOTAL_WEEKS
              + "〜"
              + CustomCycleRules.MAX_TOTAL_WEEKS
              + "週で入力してください");
    }
    List<WeekInput> weeks = in.weeks() == null ? List.of() : in.weeks();
    Set<Integer> weekNumbers = new LinkedHashSet<>();
    List<WeekInput> validWeeks = new ArrayList<>();
    for (WeekInput w : weeks) {
      if (w == null
          || w.weekNumber() == null
          || w.weekNumber() < 1
          || w.weekNumber() > totalWeeks) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "週番号が不正です");
      }
      if (!weekNumbers.add(w.weekNumber())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ週が重複しています");
      }
      BigDecimal pct = w.targetIntensityPct();
      if (pct == null
          || pct.compareTo(BigDecimal.ZERO) <= 0
          || pct.compareTo(MAX_INTENSITY_PCT) > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目標強度は0より大きく100以下で入力してください");
      }
      validWeeks.add(
          new WeekInput(
              w.weekNumber(),
              pct.setScale(1, RoundingMode.HALF_UP),
              Boolean.TRUE.equals(w.deload())));
    }
    if (weekNumbers.size() != totalWeeks) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全ての週の目標強度を入力してください");
    }
    Set<String> validParts =
        trainingMasterDao.selectAllParts().stream()
            .map(TrainingMaster::getPartCode)
            .collect(Collectors.toSet());
    Set<String> weekDayKeys = new LinkedHashSet<>();
    List<DayInput> validDays = new ArrayList<>();
    for (DayInput d : in.days() == null ? List.<DayInput>of() : in.days()) {
      if (d == null || d.weekNumber() == null || !weekNumbers.contains(d.weekNumber())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "週番号が不正です");
      }
      String dayCode = normalizeDayCode(d.dayOfWeek());
      if (!weekDayKeys.add(d.weekNumber() + ":" + dayCode)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ曜日が重複しています");
      }
      String part = d.partCode() == null || d.partCode().isBlank() ? null : d.partCode().trim();
      if (part != null && !validParts.contains(part)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "部位が不正です: " + part);
      }
      validDays.add(
          new DayInput(
              d.weekNumber(),
              dayCode,
              part,
              validateItems(d.items() == null ? List.of() : d.items())));
    }
    return new CustomCycleInput(name, totalWeeks, validWeeks, validDays);
  }

  private List<ItemInput> validateItems(List<ItemInput> items) {
    if (items == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "種目リストを指定してください");
    }
    if (items.size() > MAX_ITEMS_PER_DAY) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "種目は1日" + MAX_ITEMS_PER_DAY + "件までです");
    }
    List<ItemInput> result = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (ItemInput in : items) {
      String name = in == null || in.itemName() == null ? "" : in.itemName().trim();
      if (name.isEmpty() || name.length() > ITEM_NAME_MAX_LENGTH) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "種目名が不正です");
      }
      if (!seen.add(name)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同じ種目が重複しています: " + name);
      }
      if (trainingMasterDao.selectByItemName(name).isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "種目マスタに存在しない種目です: " + name);
      }
      Integer sets = in.targetSets();
      if (sets == null || sets < MIN_TARGET_SETS || sets > MAX_TARGET_SETS) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "セット数は" + MIN_TARGET_SETS + "〜" + MAX_TARGET_SETS + "で入力してください");
      }
      result.add(new ItemInput(name, sets));
    }
    return result;
  }

  private Optional<ProgramCycle> latestCycle(Long userId) {
    return programCycleDao.selectRecentByUserId(userId, 1).stream().findFirst();
  }

  /** 直近サイクルがCOMPLETEDかつ行き先未選択なら次の選択待ち（Q3-7）。 */
  private boolean isRenewalPending(Long userId) {
    return latestCycle(userId).map(PeriodizationService::isAwaitingRenewal).orElse(false);
  }

  /** 3択画面を出す条件: COMPLETEDかつ次の行き先が未選択（QA Q3-7追記、USER確定B）。 */
  private static boolean isAwaitingRenewal(ProgramCycle c) {
    return CycleStatus.COMPLETED.name().equals(c.getStatus()) && c.getRenewDecidedAt() == null;
  }

  private CycleDetail toCycleDetail(ProgramCycle cycle, LocalDate today, Long userId) {
    List<ProgramCycleDayTemplate> dayTemplates =
        programCycleDao.selectDayTemplatesByCycleId(cycle.getId());
    Map<Long, List<ProgramCycleDayTemplateItem>> itemsByDay =
        groupItems(programCycleDao.selectItemsByCycleId(cycle.getId()));
    Map<String, Double> oneRmCache = new HashMap<>();
    List<WeekView> weeks =
        programCycleDao.selectWeeksByCycleId(cycle.getId()).stream()
            .map(
                w ->
                    new WeekView(
                        w.getWeekNumber(),
                        w.getTargetIntensityPct(),
                        Boolean.TRUE.equals(w.getDeload()),
                        dayTemplates.stream()
                            .filter(d -> d.getWeekNumber().equals(w.getWeekNumber()))
                            .map(
                                d ->
                                    toDayView(
                                        d,
                                        itemsByDay,
                                        w.getTargetIntensityPct(),
                                        userId,
                                        oneRmCache))
                            .toList()))
            .toList();
    int current = Math.min(weekNumberOf(cycle, today), cycle.getTotalWeeks());
    return new CycleDetail(
        cycle.getId(),
        cycle.getName(),
        cycle.getTier(),
        cycle.getStatus(),
        cycle.getStartDate(),
        cycle.getTotalWeeks(),
        current,
        weeks);
  }

  private DayView toDayView(
      ProgramCycleDayTemplate d,
      Map<Long, List<ProgramCycleDayTemplateItem>> itemsByDay,
      BigDecimal intensityPct,
      Long userId,
      Map<String, Double> oneRmCache) {
    List<ItemView> items =
        itemsByDay.getOrDefault(d.getId(), List.of()).stream()
            .map(
                i ->
                    new ItemView(
                        i.getItemName(),
                        i.getDisplayOrder(),
                        i.getTargetSets(),
                        targetWeight(userId, i.getItemName(), intensityPct, oneRmCache)))
            .toList();
    return new DayView(d.getId(), d.getDayOfWeek(), d.getPartCode(), items);
  }

  /** 設計書3-2節: 目標重量 = 現在のPRからの推定1RM × 目標強度%。PR未登録ならnull。 */
  private Double targetWeight(
      Long userId, String itemName, BigDecimal intensityPct, Map<String, Double> oneRmCache) {
    if (intensityPct == null) return null;
    Double oneRm = oneRmCache.computeIfAbsent(itemName, name -> estimateCurrentOneRm(userId, name));
    if (oneRm == null) return null;
    return BigDecimal.valueOf(oneRm)
        .multiply(intensityPct)
        .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private static Map<Long, List<ProgramCycleDayTemplateItem>> groupItems(
      List<ProgramCycleDayTemplateItem> items) {
    return items.stream()
        .collect(Collectors.groupingBy(ProgramCycleDayTemplateItem::getDayTemplateId));
  }

  record StagnationSummary(StagnationLevel overall, List<ItemStagnation> items) {}

  /**
   * 種目別の保持結果から表示用の要約を作る（QA Q3-4 2026-09-24確定）。
   *
   * <ul>
   *   <li>停滞（MILD/STRONG）の種目を明示する。
   *   <li>1種目だけの停滞では全体のディロードを強く勧めない（全体の表示はMILDまで）。
   *   <li>今週がディロード週、または翌週にディロード週が計画済みなら提案を出さない。
   * </ul>
   */
  static StagnationSummary summarizeStagnation(
      Map<String, StagnationLevel> stored, List<String> targetItems, boolean deloadSoon) {
    if (deloadSoon) return new StagnationSummary(StagnationLevel.NONE, List.of());
    List<ItemStagnation> items = new ArrayList<>();
    StagnationLevel overall = StagnationLevel.NONE;
    for (String itemName : targetItems) {
      StagnationLevel level = stored.get(itemName);
      if (level == StagnationLevel.MILD || level == StagnationLevel.STRONG) {
        items.add(new ItemStagnation(itemName, level.name()));
        if (level.ordinal() > overall.ordinal()) overall = level;
      }
    }
    if (items.size() == 1 && overall == StagnationLevel.STRONG) {
      overall = StagnationLevel.MILD;
    }
    return new StagnationSummary(overall, items);
  }

  private static TodayAssignment emptyAssignment(boolean cycleCompleted) {
    return new TodayAssignment(
        false, cycleCompleted, null, null, null, null, null, null, null, "NONE", List.of());
  }

  /** start_dateからの経過日数で週番号（1始まり）を求める。開始日前は1週目扱い。 */
  static int weekNumberOf(ProgramCycle cycle, LocalDate date) {
    long days = ChronoUnit.DAYS.between(cycle.getStartDate(), date);
    if (days < 0) return 1;
    return (int) (days / 7) + 1;
  }

  static String toDayCode(DayOfWeek dow) {
    return dow.name().substring(0, 3);
  }

  private static String normalizeDayCode(String dayOfWeek) {
    if (dayOfWeek == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "曜日が不正です");
    }
    String code = dayOfWeek.trim().toUpperCase();
    if (!DAY_CODES.contains(code)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "曜日が不正です");
    }
    return code;
  }
}
