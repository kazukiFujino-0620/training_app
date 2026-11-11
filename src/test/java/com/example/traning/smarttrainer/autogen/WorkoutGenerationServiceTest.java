package com.example.traning.smarttrainer.autogen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.periodization.PeriodizationViews.CycleDetail;
import com.example.traning.periodization.PeriodizationViews.DayView;
import com.example.traning.periodization.PeriodizationViews.ItemView;
import com.example.traning.periodization.PeriodizationViews.TodayAssignment;
import com.example.traning.periodization.PeriodizationViews.WeekView;
import com.example.traning.restpreference.RestPreferenceService;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.ExclusionView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerateCommand;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GeneratedItem;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerationResult;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.Mode;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import com.example.traning.smarttrainer.recommendation.BulkingStrategy;
import com.example.traning.smarttrainer.recommendation.CuttingStrategy;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator.FatigueResult;
import com.example.traning.smarttrainer.recommendation.MaintenanceStrategy;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#4: メニュー自動作成のサービス（モード分岐・疲労・除外・入力チェック）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutGenerationServiceTest {

  private static final Long USER_ID = 5L;

  @Mock private PeriodizationService periodizationService;
  @Mock private FatigueCalculator fatigueCalculator;
  @Mock private EquipmentDao equipmentDao;
  @Mock private UserExcludedItemDao userExcludedItemDao;
  @Mock private AutogenHistoryDao autogenHistoryDao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private TrainingDao trainingDao;
  @Mock private UserDao userDao;
  @Mock private RestPreferenceService restPreferenceService;

  private WorkoutGenerationService service;
  private final Map<String, Integer> fatigue = new HashMap<>();
  private final List<TrainingItemMaster> items = new ArrayList<>();
  private final List<RecentMainSet> recentSets = new ArrayList<>();
  private final List<UserExcludedItem> exclusions = new ArrayList<>();
  private final List<Equipment> equipment = new ArrayList<>();
  private final List<TrainingItemEquipment> links = new ArrayList<>();
  private final User user = new User();

  @BeforeEach
  void setUp() {
    service =
        new WorkoutGenerationService(
            periodizationService,
            fatigueCalculator,
            new OneRmPredictionService(),
            equipmentDao,
            userExcludedItemDao,
            autogenHistoryDao,
            trainingMasterDao,
            trainingDao,
            userDao,
            restPreferenceService,
            List.of(new BulkingStrategy(), new CuttingStrategy(), new MaintenanceStrategy()));

    user.setCurrentGoalMode("BULKING");
    when(userDao.selectById(anyInt())).thenReturn(user);
    when(userDao.selectOrganizationIdById(USER_ID)).thenReturn(null);
    when(trainingMasterDao.selectAllParts())
        .thenReturn(
            List.of(
                part("CHEST", "胸"),
                part("BACK", "背中"),
                part("LEG", "脚"),
                part("SHOULDER", "肩"),
                part("ARM", "腕"),
                part("CARDIO", "カーディオ")));
    when(trainingMasterDao.selectActiveItems()).thenReturn(items);
    when(fatigueCalculator.calculate(eq(USER_ID), any()))
        .thenReturn(new FatigueResult(fatigue, Map.of(), Map.of()));
    when(autogenHistoryDao.selectRecentMainSets(eq(USER_ID), any(), anyInt()))
        .thenReturn(recentSets);
    when(userExcludedItemDao.selectByUserId(USER_ID)).thenReturn(exclusions);
    when(equipmentDao.selectByOrganizationIds(any())).thenReturn(equipment);
    when(equipmentDao.selectAllItemEquipment()).thenReturn(links);
    when(restPreferenceService.listByUserId(USER_ID)).thenReturn(List.of());
    when(trainingDao.selectByUserIdAndDateRange(anyInt(), any(), any())).thenReturn(List.of());
    when(periodizationService.getTodayAssignment(eq(USER_ID), any())).thenReturn(noProgram());

    // 各部位: 複合2つ・単関節1つ（display_order順）
    for (String p : List.of("CHEST", "BACK", "LEG", "SHOULDER", "ARM")) {
      item(p, p + "_C1", true, 1);
      item(p, p + "_C2", true, 2);
      item(p, p + "_I1", false, 3);
    }
    item("CARDIO", "ランニングマシン", false, 1);
  }

  // ===================== ヘルパー =====================

  private static TrainingMaster part(String code, String name) {
    TrainingMaster m = new TrainingMaster();
    m.setPartCode(code);
    m.setPartName(name);
    m.setOrganizationId(0L);
    return m;
  }

  private void item(String part, String name, boolean compound, int order) {
    TrainingItemMaster i = new TrainingItemMaster();
    i.setPartCode(part);
    i.setItemName(name);
    i.setIsCompound(compound);
    i.setDisplayOrder(order);
    i.setMasterFlg(1);
    items.add(i);
  }

  /** 1週間前に1セッションだけ記録がある（推定1RM＝このセットのEpley値、休止補正なし）。 */
  private void pr(String itemName, double weight, int reps) {
    set(itemName, LocalDate.now().minusDays(7), weight, reps);
  }

  private void set(String itemName, LocalDate date, double weight, int reps) {
    RecentMainSet r = new RecentMainSet();
    r.setItemName(itemName);
    r.setTrainingDate(date);
    r.setWeight(weight);
    r.setReps(reps);
    recentSets.add(r);
  }

  private static TodayAssignment noProgram() {
    return new TodayAssignment(
        false, false, null, null, null, null, null, null, null, "NONE", List.of());
  }

  private static TodayAssignment program(String partCode, String pct, List<String> itemNames) {
    DayView day =
        partCode == null
            ? null
            : new DayView(
                1L,
                "MON",
                partCode,
                itemNames.stream()
                    .map(n -> new ItemView(n, itemNames.indexOf(n) + 1, 3, null))
                    .toList());
    return new TodayAssignment(
        true, false, 9L, "筋肥大4週", 2, 4, new BigDecimal(pct), false, day, "NONE", List.of());
  }

  private GenerationResult generate(int minutes, List<String> parts) {
    return service.generate(USER_ID, new GenerateCommand(minutes, null, parts, null, null));
  }

  private static List<String> names(GenerationResult r) {
    return r.items().stream().map(GeneratedItem::itemName).toList();
  }

  private static List<String> partsOf(GenerationResult r) {
    return r.items().stream().map(GeneratedItem::partCode).distinct().toList();
  }

  // ===================== テスト =====================

  @Nested
  class プログラムとの分岐 {

    @Test
    void プログラムも部位指定も無ければおまかせで大きい部位から組む() {
      GenerationResult r = generate(90, null);

      assertThat(r.status()).isEqualTo("OK");
      assertThat(r.mode()).isEqualTo(Mode.ADHOC);
      assertThat(r.program()).isNull();
      assertThat(r.intensityPct()).isEqualTo(70.0);
      assertThat(partsOf(r)).containsExactly("LEG", "BACK", "CHEST", "SHOULDER", "ARM");
      assertThat(partsOf(r)).doesNotContain("CARDIO");
      assertThat(r.items()).allMatch(i -> i.reps() == 10);
      assertThat(r.items().get(0).reason()).isEqualTo(WorkoutGenerationService.NO_RECORD_REASON);
    }

    @Test
    void 部位指定が無くプログラムがあれば当日の部位と強度パーセントで組む() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program("LEG", "75.0", List.of("LEG_C2")));
      pr("LEG_C2", 100.0, 10); // 推定1RM 133.3 × 75% = 100 → 100.0

      GenerationResult r = generate(60, null);

      assertThat(r.mode()).isEqualTo(Mode.CYCLE_COMPLEMENT);
      assertThat(partsOf(r)).containsExactly("LEG");
      // プログラムの当日の種目を候補の先頭にする
      assertThat(names(r)).containsExactly("LEG_C2", "LEG_I1");
      GeneratedItem first = r.items().get(0);
      assertThat(first.weightKg()).isEqualTo(100.0);
      // 75%での最大回数10回 − 2 = 8回（増量の10回より少ない）
      assertThat(first.reps()).isEqualTo(8);
      assertThat(first.reason()).isEqualTo("プログラムの脚の日。目標強度 75%で計算しています");
      assertThat(r.program().weekNumber()).isEqualTo(2);
      assertThat(r.program().partName()).isEqualTo("脚");
      assertThat(r.intensityPct()).isEqualTo(75.0);
    }

    @Test
    void プログラムの当日の種目が複合2つなら両方使い_3つ目は入れ替え候補の先頭() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program("LEG", "70", List.of("LEG_C2", "LEG_C1", "LEG_I1")));

      GenerationResult r = generate(90, null);

      assertThat(names(r)).containsExactly("LEG_C2", "LEG_C1");
      assertThat(r.items().get(0).alternatives().get(0).itemName()).isEqualTo("LEG_I1");
    }

    @Test
    void プログラムの当日の種目が除外されていれば残りの種目と従来のルールで埋める() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program("LEG", "70", List.of("LEG_C2", "LEG_C1")));

      GenerationResult r =
          service.generate(USER_ID, new GenerateCommand(90, null, null, null, List.of("LEG_C1")));

      assertThat(names(r)).containsExactly("LEG_C2", "LEG_I1");
    }

    @Test
    void 部位指定があれば部位はユーザー優先で強度パーセントのみプログラム() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program("LEG", "80", List.of("LEG_C1")));
      pr("CHEST_C1", 100.0, 1);

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(r.mode()).isEqualTo(Mode.USER_OVERRIDE);
      assertThat(partsOf(r)).containsExactly("CHEST");
      GeneratedItem first = r.items().get(0);
      assertThat(first.weightKg()).isEqualTo(80.0);
      assertThat(first.reps()).isEqualTo(5);
      assertThat(first.reason()).isEqualTo("胸を指定。強度はプログラムの 80%で計算しています");
    }

    @Test
    void プログラムが無く部位指定があれば目的別の強度で組む() {
      pr("CHEST_C1", 100.0, 1);

      GenerationResult r = generate(60, List.of("CHEST", "ARM"));

      assertThat(r.mode()).isEqualTo(Mode.ADHOC);
      assertThat(names(r)).containsExactly("CHEST_C1", "ARM_C1", "CHEST_I1");
      assertThat(r.items().get(0).weightKg()).isEqualTo(70.0);
      assertThat(r.items().get(0).reason()).isEqualTo("胸を指定。複合種目を先にしています");
    }

    @Test
    void プログラムの休養日で部位指定が無ければおまかせで通常どおり組み_強度は今週のプログラムの割合() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program(null, "80", List.of()));
      pr("LEG_C1", 100.0, 1);

      GenerationResult r = generate(90, null);

      assertThat(r.mode()).isEqualTo(Mode.ADHOC);
      assertThat(r.program().partCode()).isNull();
      assertThat(r.program().weekNumber()).isEqualTo(2);
      assertThat(r.intensityPct()).isEqualTo(80.0);
      // 80%での最大回数7.5回 − 2 = 5回、セットは減らさない
      assertThat(r.items()).allMatch(i -> i.reps() == 5 && i.sets() == 4);
      assertThat(r.items().get(0).weightKg()).isEqualTo(80.0);
      assertThat(r.items().get(0).reason()).isEqualTo("疲労の少ない部位から選び、強度はプログラムの 80%で計算しています");
    }

    @Test
    void プログラムの休養日でも部位を指定すれば強度パーセントのみプログラム() {
      when(periodizationService.getTodayAssignment(eq(USER_ID), any()))
          .thenReturn(program(null, "80", List.of()));

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(r.mode()).isEqualTo(Mode.USER_OVERRIDE);
      assertThat(r.intensityPct()).isEqualTo(80.0);
      assertThat(r.items()).allMatch(i -> i.sets() == 4);
    }

    @Test
    void 未来日はサイクルの全体像から読み取りのみで当日の枠を求める() {
      LocalDate start = LocalDate.now().minusDays(3);
      LocalDate target = LocalDate.now().plusDays(7); // 2週目
      String code = PeriodizationService.toDayCode(target.getDayOfWeek());
      CycleDetail detail =
          new CycleDetail(
              9L,
              "筋肥大4週",
              "BEGINNER_PRESET",
              "ACTIVE",
              start,
              4,
              1,
              List.of(
                  new WeekView(1, new BigDecimal("65"), false, List.of()),
                  new WeekView(
                      2,
                      new BigDecimal("70"),
                      false,
                      List.of(new DayView(2L, code, "BACK", List.of())))));
      when(periodizationService.getActiveCycleDetail(USER_ID, target))
          .thenReturn(Optional.of(detail));

      GenerationResult r =
          service.generate(USER_ID, new GenerateCommand(60, target, null, null, null));

      verify(periodizationService, never()).getTodayAssignment(anyLong(), any());
      assertThat(r.mode()).isEqualTo(Mode.CYCLE_COMPLEMENT);
      assertThat(partsOf(r)).containsExactly("BACK");
      assertThat(r.program().weekNumber()).isEqualTo(2);
      assertThat(r.intensityPct()).isEqualTo(70.0);
    }

    @Test
    void 減量目的は強度60パーセント_12回_3セット() {
      user.setCurrentGoalMode("CUTTING");
      GenerationResult r = generate(90, List.of("ARM"));
      assertThat(r.intensityPct()).isEqualTo(60.0);
      assertThat(r.items()).allMatch(i -> i.reps() == 12 && i.sets() == 3);
    }

    @Test
    void 維持目的は強度70パーセント_8回() {
      user.setCurrentGoalMode("MAINTENANCE");
      GenerationResult r = generate(90, List.of("ARM"));
      assertThat(r.items()).allMatch(i -> i.reps() == 8 && i.sets() == 3);
    }
  }

  @Nested
  class 疲労の扱い {

    @Test
    void おまかせでは高疲労の部位を候補から外す() {
      fatigue.put("CHEST", 71);
      fatigue.put("BACK", 65); // しきい値ちょうどは外さない

      GenerationResult r = generate(90, null);

      assertThat(partsOf(r)).doesNotContain("CHEST").contains("BACK");
      assertThat(r.items()).allMatch(i -> i.warning() == null);
    }

    @Test
    void 前日か当日に本番セットがある部位は疲労度が低くても高疲労として外す() {
      fatigue.put("LEG", 10);
      when(autogenHistoryDao.selectPartsWithMainSets(eq(USER_ID), any(), any()))
          .thenReturn(List.of("LEG"));

      GenerationResult r = generate(90, null);

      assertThat(partsOf(r)).doesNotContain("LEG").contains("BACK");
      ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
      verify(autogenHistoryDao).selectPartsWithMainSets(eq(USER_ID), from.capture(), to.capture());
      assertThat(from.getValue()).isEqualTo(LocalDate.now().minusDays(1));
      assertThat(to.getValue()).isEqualTo(LocalDate.now());
    }

    @Test
    void 前日と2日前に行い疲労度が約60パーセントの部位も_前日の本番セットで外す() {
      // 2日前と前日に同量: (0.5 + 0.707) / 2 ≒ 60% で65%以下だが、前日の本番セットで高疲労
      fatigue.put("CHEST", 60);
      when(autogenHistoryDao.selectPartsWithMainSets(eq(USER_ID), any(), any()))
          .thenReturn(List.of("CHEST"));

      GenerationResult r = generate(90, null);

      assertThat(partsOf(r)).doesNotContain("CHEST").contains("LEG", "BACK");
    }

    @Test
    void 未来日は前日をその日の前日として本番セットを見る() {
      LocalDate target = LocalDate.now().plusDays(3);
      when(periodizationService.getActiveCycleDetail(USER_ID, target)).thenReturn(Optional.empty());

      service.generate(USER_ID, new GenerateCommand(60, target, null, null, null));

      verify(autogenHistoryDao).selectPartsWithMainSets(USER_ID, target.minusDays(1), target);
    }

    @Test
    void 指定した部位は前日に本番セットがあっても外さず注意文のみ() {
      when(autogenHistoryDao.selectPartsWithMainSets(eq(USER_ID), any(), any()))
          .thenReturn(List.of("CHEST"));

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(partsOf(r)).containsExactly("CHEST");
      assertThat(r.items())
          .allMatch(i -> WorkoutGenerationService.FATIGUE_WARNING.equals(i.warning()));
    }

    @Test
    void おまかせで全部位が高疲労なら外さず各種目を1セット減らして理由を出す() {
      for (String p : List.of("CHEST", "BACK", "LEG", "SHOULDER", "ARM")) fatigue.put(p, 90);
      pr("LEG_C1", 100.0, 1);

      GenerationResult r = generate(240, null);

      assertThat(partsOf(r)).hasSize(5);
      assertThat(r.items()).allMatch(i -> i.sets() == 3);
      assertThat(r.items().get(0).reason()).isEqualTo("全部位に疲労が残っているため、セット数を1つ減らしています");
    }

    @Test
    void 指定した部位は高疲労でも外さず注意文のみ() {
      fatigue.put("CHEST", 90);

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(partsOf(r)).containsExactly("CHEST");
      assertThat(r.items()).allMatch(i -> i.sets() == 4);
      assertThat(r.items())
          .allMatch(i -> WorkoutGenerationService.FATIGUE_WARNING.equals(i.warning()));
    }
  }

  @Nested
  class 候補の条件 {

    @Test
    void 保存済みの除外とその場の除外を候補と入れ替え候補から外す() {
      UserExcludedItem saved = new UserExcludedItem();
      saved.setItemName("CHEST_C1");
      saved.setNextConfirmationDueAt(LocalDateTime.now().plusDays(3));
      exclusions.add(saved);

      GenerationResult r =
          service.generate(
              USER_ID, new GenerateCommand(60, null, List.of("CHEST"), null, List.of("CHEST_I1")));

      assertThat(names(r)).containsExactly("CHEST_C2");
      assertThat(r.items().get(0).alternatives()).isEmpty();
      assertThat(r.itemsNeedingReconfirmation()).isEmpty();
    }

    @Test
    void 器具の紐付けが無い種目は器具で除外しない_紐付けがあれば全部の器具が必要() {
      Equipment barbell = equipment(1L, "バーベル", "FREE_WEIGHT", null);
      Equipment bench = equipment(2L, "ベンチ・ラック", "BENCH_RACK", null);
      equipment.add(barbell);
      equipment.add(bench);
      link("CHEST_C1", 1L);
      link("CHEST_C1", 2L);

      GenerationResult onlyBarbell =
          service.generate(
              USER_ID, new GenerateCommand(60, null, List.of("CHEST"), List.of(1L), null));
      assertThat(names(onlyBarbell)).containsExactly("CHEST_C2", "CHEST_I1");

      GenerationResult both =
          service.generate(
              USER_ID, new GenerateCommand(60, null, List.of("CHEST"), List.of(1L, 2L), null));
      assertThat(names(both)).containsExactly("CHEST_C1", "CHEST_I1");

      GenerationResult none =
          service.generate(
              USER_ID, new GenerateCommand(60, null, List.of("CHEST"), List.of(), null));
      assertThat(names(none)).containsExactly("CHEST_C2", "CHEST_I1");
    }

    @Test
    void 器具の刻みと自重種目は紐付いた器具から決める() {
      equipment.add(equipment(1L, "ダンベル", "FREE_WEIGHT", "2.00"));
      equipment.add(equipment(2L, "自重", "BODYWEIGHT", null));
      link("CHEST_C1", 1L);
      link("ARM_C1", 2L);
      pr("CHEST_C1", 35.0, 1); // 24.5 → 24
      pr("ARM_C1", 50.0, 1);

      GenerationResult r = generate(90, List.of("CHEST", "ARM"));

      GeneratedItem chest = r.items().get(0);
      GeneratedItem arm = r.items().get(1);
      assertThat(chest.weightKg()).isEqualTo(24.0);
      assertThat(arm.bodyweight()).isTrue();
      assertThat(arm.weightKg()).isNull();
      assertThat(arm.reason()).endsWith("自重種目のため回数のみです");
    }

    @Test
    void 回数12回以下の記録が無ければ直近の本番セットの重量をそのまま入れる() {
      set("CHEST_C1", LocalDate.now().minusDays(3), 42.5, 15);
      set("CHEST_C1", LocalDate.now().minusDays(3), 40.0, 20);

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(r.items().get(0).weightKg()).isEqualTo(42.5);
      assertThat(r.items().get(0).reason()).endsWith("推定1RMが無いため前回の重量を入れています");
    }

    @Test
    void 条件に合う種目が無ければNO_CANDIDATES() {
      GenerationResult r =
          service.generate(
              USER_ID,
              new GenerateCommand(
                  60,
                  null,
                  List.of("SHOULDER"),
                  null,
                  List.of("SHOULDER_C1", "SHOULDER_C2", "SHOULDER_I1")));
      assertThat(r.status()).isEqualTo("NO_CANDIDATES");
      assertThat(r.items()).isEmpty();
    }

    private Equipment equipment(Long id, String name, String category, String inc) {
      Equipment e = new Equipment();
      e.setId(id);
      e.setName(name);
      e.setCategory(category);
      e.setWeightIncrementKg(inc != null ? new BigDecimal(inc) : null);
      return e;
    }

    private void link(String itemName, Long equipmentId) {
      TrainingItemEquipment l = new TrainingItemEquipment();
      l.setItemName(itemName);
      l.setEquipmentId(equipmentId);
      links.add(l);
    }
  }

  @Nested
  class 直近の記録からの重量_Q4_12 {

    @Test
    void 実データ相当_スクワットは直近3回の平均から82_5kgで10回() {
      LocalDate today = LocalDate.now();
      // 10/03 120×5（推定140）、10/11 85×8（推定107.7）、10/17 80×10（推定106.7）→ 平均118.1 × 70% = 82.7 → 82.5
      set("LEG_C1", today.minusDays(14), 120.0, 5);
      set("LEG_C1", today.minusDays(6), 85.0, 8);
      set("LEG_C1", today.minusDays(6), 60.0, 12);
      set("LEG_C1", today, 80.0, 10);
      set("LEG_C1", today, 80.0, 10);

      GenerationResult r = generate(60, List.of("LEG"));

      GeneratedItem squat = r.items().get(0);
      assertThat(squat.itemName()).isEqualTo("LEG_C1");
      assertThat(squat.weightKg()).isEqualTo(82.5);
      assertThat(squat.reps()).isEqualTo(10);
      assertThat(squat.reason()).doesNotContain(WorkoutGenerationService.LONG_BREAK_REASON);
    }

    @Test
    void 生涯ベストは使わず_久しぶりの種目は理由欄で知らせる() {
      set("CHEST_C1", LocalDate.now().minusDays(40), 100.0, 1);

      GenerationResult r = generate(60, List.of("CHEST"));

      // 100 × 70% × 0.9 = 63 → 62.5
      assertThat(r.items().get(0).weightKg()).isEqualTo(62.5);
      assertThat(r.items().get(0).reason()).isEqualTo("胸を指定。複合種目を先にしています。久しぶりの種目のため軽めにしています");
    }
  }

  @Nested
  class おまかせの部位の優先度_Q4_13 {

    private void lastTrained(String part, Integer daysAgo) {
      PartLastTrained p = new PartLastTrained();
      p.setPartCode(part);
      p.setLastDate(LocalDate.now().minusDays(daysAgo));
      lastDates.add(p);
    }

    private final List<PartLastTrained> lastDates = new ArrayList<>();

    @BeforeEach
    void stub() {
      when(autogenHistoryDao.selectLastTrainedDateByPart(eq(USER_ID), any())).thenReturn(lastDates);
    }

    @Test
    void 最後にやってからの日数が長い順で_未実施が最優先_同日数は大きい部位() {
      // ARM は未実施
      assertThat(
              WorkoutGenerationService.omakaseParts(
                  new java.util.LinkedHashSet<>(List.of("CHEST", "BACK", "LEG", "SHOULDER", "ARM")),
                  Map.of(
                      "LEG", LocalDate.now().minusDays(2),
                      "BACK", LocalDate.now().minusDays(3),
                      "CHEST", LocalDate.now().minusDays(9),
                      "SHOULDER", LocalDate.now().minusDays(9)),
                  LocalDate.now()))
          .containsExactly("ARM", "CHEST", "SHOULDER", "BACK", "LEG");
    }

    @Test
    void 時間不足では最近やった部位から外し_表示は大きい部位から() {
      lastTrained("LEG", 2);
      lastTrained("BACK", 3);
      lastTrained("CHEST", 9);
      lastTrained("SHOULDER", 9);

      // 25分: 優先度 ARM > CHEST > SHOULDER > BACK > LEG。最近やった脚・背中から外れる
      GenerationResult r = generate(25, null);

      assertThat(partsOf(r)).doesNotContain("LEG", "BACK");
      assertThat(partsOf(r)).contains("ARM");
      // 表示順は大きい部位→小さい部位（複合→単関節）
      List<String> compoundParts =
          r.items().stream().filter(GeneratedItem::compound).map(GeneratedItem::partCode).toList();
      assertThat(compoundParts)
          .isSortedAccordingTo(java.util.Comparator.comparingInt(AutogenRules::partSizeRank));
    }

    @Test
    void 高疲労の部位を外してから優先度を付ける() {
      fatigue.put("ARM", 90);
      lastTrained("LEG", 20);

      GenerationResult r = generate(90, null);

      assertThat(partsOf(r)).doesNotContain("ARM");
    }
  }

  @Nested
  class 除外の再確認 {

    @Test
    void 新規登録時は2週間後に最初の再確認() {
      when(userExcludedItemDao.selectByUserIdAndItemName(USER_ID, "CHEST_C1"))
          .thenReturn(Optional.empty());

      ExclusionView v = service.addExclusion(USER_ID, " CHEST_C1 ", " 右肩に違和感 ");

      ArgumentCaptor<UserExcludedItem> cap = ArgumentCaptor.forClass(UserExcludedItem.class);
      verify(userExcludedItemDao).insert(cap.capture());
      UserExcludedItem saved = cap.getValue();
      assertThat(saved.getUserId()).isEqualTo(USER_ID);
      assertThat(saved.getItemName()).isEqualTo("CHEST_C1");
      assertThat(saved.getReason()).isEqualTo("右肩に違和感");
      assertThat(ChronoUnit.DAYS.between(saved.getCreatedAt(), saved.getNextConfirmationDueAt()))
          .isEqualTo(14);
      assertThat(v.needsReconfirmation()).isFalse();
      assertThat(v.partName()).isEqualTo("胸");
    }

    @Test
    void まだ必要と答えたら次の再確認は4週間後() {
      UserExcludedItem e = new UserExcludedItem();
      e.setUserId(USER_ID);
      e.setItemName("CHEST_C1");
      e.setNextConfirmationDueAt(LocalDateTime.now().minusDays(1));
      when(userExcludedItemDao.selectByUserIdAndItemName(USER_ID, "CHEST_C1"))
          .thenReturn(Optional.of(e));

      service.confirmExclusion(USER_ID, "CHEST_C1", true);

      verify(userExcludedItemDao).update(e);
      verify(userExcludedItemDao, never()).delete(any());
      assertThat(e.getNextConfirmationDueAt())
          .isCloseTo(LocalDateTime.now().plusDays(28), within(5, ChronoUnit.SECONDS));
      assertThat(e.getLastConfirmedAt())
          .isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void 解除すると答えたら削除する() {
      UserExcludedItem e = new UserExcludedItem();
      when(userExcludedItemDao.selectByUserIdAndItemName(USER_ID, "CHEST_C1"))
          .thenReturn(Optional.of(e));

      service.confirmExclusion(USER_ID, "CHEST_C1", false);

      verify(userExcludedItemDao).delete(e);
      verify(userExcludedItemDao, never()).update(any());
    }

    @Test
    void 日付の計算() {
      LocalDateTime t = LocalDateTime.of(2026, 10, 26, 9, 0);
      assertThat(WorkoutGenerationService.firstConfirmationDueAt(t))
          .isEqualTo(LocalDateTime.of(2026, 11, 9, 9, 0));
      assertThat(WorkoutGenerationService.nextConfirmationDueAt(t))
          .isEqualTo(LocalDateTime.of(2026, 11, 23, 9, 0));
    }

    @Test
    void 期限を過ぎた除外は生成結果で再確認を求める_自動では解除しない() {
      UserExcludedItem due = new UserExcludedItem();
      due.setItemName("CHEST_C1");
      due.setCreatedAt(LocalDateTime.of(2026, 10, 26, 9, 0));
      due.setNextConfirmationDueAt(LocalDateTime.now().minusMinutes(1));
      exclusions.add(due);

      GenerationResult r = generate(60, List.of("CHEST"));

      assertThat(r.itemsNeedingReconfirmation())
          .extracting(ExclusionView::itemName)
          .containsExactly("CHEST_C1");
      assertThat(r.itemsNeedingReconfirmation().get(0).excludedSince())
          .isEqualTo(LocalDate.of(2026, 10, 26));
      assertThat(names(r)).doesNotContain("CHEST_C1");
      verify(userExcludedItemDao, never()).delete(any());
    }

    @Test
    void 期限ちょうどは再確認の対象() {
      LocalDateTime now = LocalDateTime.of(2026, 11, 9, 9, 0);
      UserExcludedItem e = new UserExcludedItem();
      e.setNextConfirmationDueAt(now);
      assertThat(WorkoutGenerationService.isConfirmationDue(e, now)).isTrue();
      e.setNextConfirmationDueAt(now.plusSeconds(1));
      assertThat(WorkoutGenerationService.isConfirmationDue(e, now)).isFalse();
    }

    @Test
    void 種目マスタに無い種目は除外できない_同じ種目は二重登録しない() {
      assertThatThrownBy(() -> service.addExclusion(USER_ID, "存在しない種目", null))
          .isInstanceOf(ResponseStatusException.class)
          .extracting(e -> ((ResponseStatusException) e).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);

      when(userExcludedItemDao.selectByUserIdAndItemName(USER_ID, "CHEST_C1"))
          .thenReturn(Optional.of(new UserExcludedItem()));
      assertThatThrownBy(() -> service.addExclusion(USER_ID, "CHEST_C1", null))
          .extracting(e -> ((ResponseStatusException) e).getStatusCode())
          .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 自分の除外に無い種目は確認も削除もできない() {
      when(userExcludedItemDao.selectByUserIdAndItemName(USER_ID, "CHEST_C1"))
          .thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.confirmExclusion(USER_ID, "CHEST_C1", true))
          .extracting(e -> ((ResponseStatusException) e).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
      assertThatThrownBy(() -> service.removeExclusion(USER_ID, "CHEST_C1"))
          .extracting(e -> ((ResponseStatusException) e).getStatusCode())
          .isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  class 入力チェック {

    private void assertBadRequest(GenerateCommand cmd) {
      assertThatThrownBy(() -> service.generate(USER_ID, cmd))
          .isInstanceOf(ResponseStatusException.class)
          .extracting(e -> ((ResponseStatusException) e).getStatusCode())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 時間は15分以上() {
      assertBadRequest(new GenerateCommand(14, null, null, null, null));
      assertBadRequest(new GenerateCommand(null, null, null, null, null));
      assertBadRequest(new GenerateCommand(241, null, null, null, null));
      assertThat(
              service.generate(USER_ID, new GenerateCommand(15, null, null, null, null)).status())
          .isEqualTo("OK");
    }

    @Test
    void 部位コードは部位マスタにあるもののみ_カーディオは不可() {
      assertBadRequest(new GenerateCommand(60, null, List.of("CARDIO"), null, null));
      assertBadRequest(new GenerateCommand(60, null, List.of("NECK"), null, null));
    }

    @Test
    void 部位の重複は指定順を保って1つにする() {
      GenerationResult r = generate(90, List.of("ARM", "CHEST", "ARM"));
      assertThat(partsOf(r)).containsExactly("ARM", "CHEST");
    }

    @Test
    void 器具IDは見える器具のみ() {
      Equipment e = new Equipment();
      e.setId(1L);
      equipment.add(e);
      assertBadRequest(new GenerateCommand(60, null, null, List.of(99L), null));
    }

    @Test
    void 過去日は不可() {
      assertBadRequest(new GenerateCommand(60, LocalDate.now().minusDays(1), null, null, null));
    }
  }
}
