package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.periodization.PeriodizationViews.ItemInput;
import com.example.traning.periodization.PeriodizationViews.TodayAssignment;
import com.example.traning.periodization.StagnationDetectionService.StagnationLevel;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#3: PeriodizationServiceの採用・編集・当日割当・サイクル終了時の選択。 */
@ExtendWith(MockitoExtension.class)
class PeriodizationServiceTest {

  @Mock private ProgramCycleDao programCycleDao;
  @Mock private ProgramCycleProposalDao programCycleProposalDao;
  @Mock private PresetProgramDao presetProgramDao;
  @Mock private StagnationDetectionService stagnationDetectionService;
  @Mock private PersonalRecordService personalRecordService;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private UserDao userDao;

  private PeriodizationService service;
  private final AtomicLong ids = new AtomicLong(1000);

  private static final Long USER = 5L;
  private static final LocalDate START = LocalDate.of(2026, 9, 7); // 月曜

  @BeforeEach
  void setUp() {
    service =
        new PeriodizationService(
            programCycleDao,
            programCycleProposalDao,
            presetProgramDao,
            stagnationDetectionService,
            personalRecordService,
            new OneRmPredictionService(),
            trainingMasterDao,
            userDao);
    // ユーザーの所属店舗は2（親組織1のプリセットは参照範囲外＝USER確定の案B）
    lenient().when(userDao.selectOrganizationIdById(USER)).thenReturn(2L);
  }

  private PresetProgram preset(Long orgId) {
    PresetProgram p = new PresetProgram();
    p.setId(7L);
    p.setOrganizationId(orgId);
    p.setName("王道4週");
    p.setPurposeCategory("BULK");
    p.setTotalWeeks(4);
    return p;
  }

  private void stubPresetContents() {
    PresetProgramWeek w = new PresetProgramWeek();
    w.setWeekNumber(4);
    w.setTargetIntensityPct(new BigDecimal("60.0"));
    w.setDeload(true);
    when(presetProgramDao.selectWeeksByPresetId(7L)).thenReturn(List.of(w));
    PresetProgramDayTemplate d = new PresetProgramDayTemplate();
    d.setId(70L);
    d.setWeekNumber(1);
    d.setDayOfWeek("MON");
    d.setPartCode("CHEST");
    when(presetProgramDao.selectDayTemplatesByPresetId(7L)).thenReturn(List.of(d));
    PresetProgramDayTemplateItem i1 = new PresetProgramDayTemplateItem();
    i1.setDayTemplateId(70L);
    i1.setItemName("ベンチプレス");
    i1.setDisplayOrder(1);
    i1.setTargetSets(3);
    PresetProgramDayTemplateItem i2 = new PresetProgramDayTemplateItem();
    i2.setDayTemplateId(70L);
    i2.setItemName("ディップス");
    i2.setDisplayOrder(2);
    i2.setTargetSets(3);
    when(presetProgramDao.selectItemsByPresetId(7L)).thenReturn(List.of(i1, i2));
    doAnswer(
            inv -> {
              ((ProgramCycle) inv.getArgument(0)).setId(ids.incrementAndGet());
              return 1;
            })
        .when(programCycleDao)
        .insert(any(ProgramCycle.class));
    doAnswer(
            inv -> {
              ((ProgramCycleDayTemplate) inv.getArgument(0)).setId(ids.incrementAndGet());
              return 1;
            })
        .when(programCycleDao)
        .insertDayTemplate(any(ProgramCycleDayTemplate.class));
  }

  @Test
  void adoptPreset_既存ACTIVEを退避しプリセット構成と基準1RMをコピーする() {
    when(presetProgramDao.selectById(7L)).thenReturn(Optional.of(preset(0L)));
    ProgramCycle old = new ProgramCycle();
    old.setId(99L);
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.of(old));
    stubPresetContents();
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(90.0);
    pr.setMaxReps(5);
    when(personalRecordService.getByUserIdAndItem(USER, "ベンチプレス")).thenReturn(Optional.of(pr));
    when(personalRecordService.getByUserIdAndItem(USER, "ディップス")).thenReturn(Optional.empty());

    Long cycleId = service.adoptPreset(USER, 7L, START);

    verify(programCycleDao).archiveById(99L);
    // 自分で別のプログラムに切り替えたので、予約中のトレーナーの案は取り消す（2026-09-26 USER確定B）
    verify(programCycleProposalDao).cancelScheduledBySwitch(USER);
    ArgumentCaptor<ProgramCycle> cycle = ArgumentCaptor.forClass(ProgramCycle.class);
    verify(programCycleDao).insert(cycle.capture());
    assertThat(cycle.getValue().getTier()).isEqualTo("BEGINNER_PRESET");
    assertThat(cycle.getValue().getStatus()).isEqualTo("ACTIVE");
    assertThat(cycle.getValue().getSourcePresetId()).isEqualTo(7L);
    assertThat(cycleId).isEqualTo(cycle.getValue().getId());

    ArgumentCaptor<ProgramCycleWeek> week = ArgumentCaptor.forClass(ProgramCycleWeek.class);
    verify(programCycleDao).insertWeek(week.capture());
    assertThat(week.getValue().getDeload()).isTrue();

    ArgumentCaptor<ProgramCycleDayTemplateItem> items =
        ArgumentCaptor.forClass(ProgramCycleDayTemplateItem.class);
    verify(programCycleDao, org.mockito.Mockito.times(2)).insertDayTemplateItem(items.capture());
    assertThat(items.getAllValues())
        .extracting(ProgramCycleDayTemplateItem::getItemName)
        .containsExactly("ベンチプレス", "ディップス");

    // PR未登録のディップスはスキップ。ベンチ: 90×(1+5/30)=105.0
    ArgumentCaptor<ProgramCycleItemBaseline> baseline =
        ArgumentCaptor.forClass(ProgramCycleItemBaseline.class);
    verify(programCycleDao).insertItemBaseline(baseline.capture());
    assertThat(baseline.getValue().getItemName()).isEqualTo("ベンチプレス");
    assertThat(baseline.getValue().getBaselineOneRm()).isEqualByComparingTo("105.0");
  }

  @Test
  void adoptPreset_参照範囲外の組織のプリセットは404() {
    when(presetProgramDao.selectById(7L)).thenReturn(Optional.of(preset(3L)));
    assertThatThrownBy(() -> service.adoptPreset(USER, 7L, START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void visibleOrganizationIds_全組織共通と自店舗のみ() {
    assertThat(service.visibleOrganizationIds(USER)).containsExactly(0L, 2L);
  }

  @Test
  void adoptPreset_自店舗のプリセットは採用できる() {
    when(presetProgramDao.selectById(7L)).thenReturn(Optional.of(preset(2L)));
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    stubPresetContents();
    assertThat(service.adoptPreset(USER, 7L, START)).isNotNull();
  }

  @Test
  void adoptPreset_親組織のプリセットIDを直接指定しても404() {
    when(presetProgramDao.selectById(7L)).thenReturn(Optional.of(preset(1L)));
    assertThatThrownBy(() -> service.adoptPreset(USER, 7L, START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(programCycleDao, never()).insert(any());
  }

  private ProgramCycle activeCycle(Long ownerId, String tier) {
    ProgramCycle c = new ProgramCycle();
    c.setId(10L);
    c.setUserId(ownerId);
    c.setTier(tier);
    c.setStatus("ACTIVE");
    c.setStartDate(START);
    c.setTotalWeeks(4);
    return c;
  }

  private void stubDayTemplate(ProgramCycle c) {
    ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
    d.setId(200L);
    d.setCycleId(c.getId());
    when(programCycleDao.selectDayTemplateById(200L)).thenReturn(Optional.of(d));
    when(programCycleDao.selectById(10L)).thenReturn(Optional.of(c));
  }

  @Test
  void customizeDayTemplateItems_全置換しtierは変更しない() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET");
    stubDayTemplate(c);
    when(trainingMasterDao.selectByItemName(anyString()))
        .thenReturn(Optional.of(new TrainingItemMaster()));

    service.customizeDayTemplateItems(
        USER, 200L, List.of(new ItemInput("スクワット", 5), new ItemInput(" ベンチプレス ", 3)));

    verify(programCycleDao).deleteItemsByDayTemplateId(200L);
    ArgumentCaptor<ProgramCycleDayTemplateItem> items =
        ArgumentCaptor.forClass(ProgramCycleDayTemplateItem.class);
    verify(programCycleDao, org.mockito.Mockito.times(2)).insertDayTemplateItem(items.capture());
    assertThat(items.getAllValues())
        .extracting(
            ProgramCycleDayTemplateItem::getItemName, ProgramCycleDayTemplateItem::getDisplayOrder)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("スクワット", 1),
            org.assertj.core.groups.Tuple.tuple("ベンチプレス", 2));
    // QA Q3-6見直し: プリセットを編集してもtierは変えない（insert/updateとも発生しない）
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void customizeDayTemplateItems_他人のサイクルは403() {
    stubDayTemplate(activeCycle(999L, "BEGINNER_PRESET"));
    assertThatThrownBy(() -> service.customizeDayTemplateItems(USER, 200L, List.of()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403");
    verify(programCycleDao, never()).deleteItemsByDayTemplateId(anyLong());
  }

  @Test
  void customizeDayTemplateItems_種目マスタに無い種目は400() {
    stubDayTemplate(activeCycle(USER, "BEGINNER_PRESET"));
    when(trainingMasterDao.selectByItemName("謎の種目")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () -> service.customizeDayTemplateItems(USER, 200L, List.of(new ItemInput("謎の種目", 3))))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    verify(programCycleDao, never()).deleteItemsByDayTemplateId(anyLong());
  }

  @Test
  void getTodayAssignment_total_weeks経過後はCOMPLETEDにして次サイクルは作らない() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET");
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.of(c));

    TodayAssignment t = service.getTodayAssignment(USER, START.plusWeeks(4));

    assertThat(t.cycleCompleted()).isTrue();
    assertThat(t.dayTemplate()).isNull();
    verify(programCycleDao).completeById(10L);
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void getTodayAssignment_当日割当と目標重量と停滞警告を返す() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET");
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.of(c));
    ProgramCycleWeek w2 = new ProgramCycleWeek();
    w2.setWeekNumber(2);
    w2.setTargetIntensityPct(new BigDecimal("75.0"));
    w2.setDeload(false);
    when(programCycleDao.selectWeeksByCycleId(10L)).thenReturn(List.of(w2));
    ProgramCycleDayTemplate mon = new ProgramCycleDayTemplate();
    mon.setId(300L);
    mon.setWeekNumber(2);
    mon.setDayOfWeek("MON");
    mon.setPartCode("CHEST");
    when(programCycleDao.selectDayTemplatesByCycleId(10L)).thenReturn(List.of(mon));
    ProgramCycleDayTemplateItem item = new ProgramCycleDayTemplateItem();
    item.setDayTemplateId(300L);
    item.setItemName("ベンチプレス");
    item.setDisplayOrder(1);
    item.setTargetSets(3);
    when(programCycleDao.selectItemsByCycleId(10L)).thenReturn(new ArrayList<>(List.of(item)));
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(100.0);
    pr.setMaxReps(1);
    when(personalRecordService.getByUserIdAndItem(USER, "ベンチプレス")).thenReturn(Optional.of(pr));
    when(stagnationDetectionService.getStoredLevels(USER, List.of("ベンチプレス")))
        .thenReturn(java.util.Map.of("ベンチプレス", StagnationLevel.STRONG));

    TodayAssignment t = service.getTodayAssignment(USER, START.plusDays(7)); // 2週目の月曜

    assertThat(t.hasActiveCycle()).isTrue();
    assertThat(t.weekNumber()).isEqualTo(2);
    assertThat(t.dayTemplate().partCode()).isEqualTo("CHEST");
    assertThat(t.dayTemplate().items().get(0).targetWeightKg()).isEqualTo(75.0);
    // 保持結果はSTRONGだが、1種目だけの停滞なので全体はMILDまで（QA Q3-4 2026-09-24確定）
    assertThat(t.stagnationWarning()).isEqualTo("MILD");
    assertThat(t.stagnantItems())
        .containsExactly(new PeriodizationViews.ItemStagnation("ベンチプレス", "STRONG"));
  }

  @Test
  void summarizeStagnation_複数種目が停滞しSTRONGを含めば全体STRONG() {
    var summary =
        PeriodizationService.summarizeStagnation(
            java.util.Map.of(
                "ベンチプレス", StagnationLevel.STRONG,
                "スクワット", StagnationLevel.MILD,
                "デッドリフト", StagnationLevel.NONE),
            List.of("ベンチプレス", "スクワット", "デッドリフト", "ディップス"),
            false);
    assertThat(summary.overall()).isEqualTo(StagnationLevel.STRONG);
    assertThat(summary.items())
        .extracting(PeriodizationViews.ItemStagnation::itemName)
        .containsExactly("ベンチプレス", "スクワット");
  }

  @Test
  void summarizeStagnation_判定不可や未判定の種目は停滞として扱わない() {
    var summary =
        PeriodizationService.summarizeStagnation(
            java.util.Map.of("ベンチプレス", StagnationLevel.INSUFFICIENT),
            List.of("ベンチプレス", "スクワット"),
            false);
    assertThat(summary.overall()).isEqualTo(StagnationLevel.NONE);
    assertThat(summary.items()).isEmpty();
  }

  @Test
  void summarizeStagnation_今週か翌週がディロード週なら提案を出さない() {
    var summary =
        PeriodizationService.summarizeStagnation(
            java.util.Map.of("ベンチプレス", StagnationLevel.STRONG, "スクワット", StagnationLevel.STRONG),
            List.of("ベンチプレス", "スクワット"),
            true);
    assertThat(summary.overall()).isEqualTo(StagnationLevel.NONE);
    assertThat(summary.items()).isEmpty();
  }

  @Test
  void getTodayAssignment_翌週がディロード週なら停滞提案を出さない() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET");
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.of(c));
    ProgramCycleWeek w3 = new ProgramCycleWeek();
    w3.setWeekNumber(3);
    w3.setTargetIntensityPct(new BigDecimal("80.0"));
    w3.setDeload(false);
    ProgramCycleWeek w4 = new ProgramCycleWeek();
    w4.setWeekNumber(4);
    w4.setTargetIntensityPct(new BigDecimal("60.0"));
    w4.setDeload(true);
    when(programCycleDao.selectWeeksByCycleId(10L)).thenReturn(List.of(w3, w4));
    lenient()
        .when(stagnationDetectionService.getStoredLevels(any(), any()))
        .thenReturn(java.util.Map.of("ベンチプレス", StagnationLevel.STRONG));

    TodayAssignment t = service.getTodayAssignment(USER, START.plusDays(14)); // 3週目

    assertThat(t.weekNumber()).isEqualTo(3);
    assertThat(t.stagnationWarning()).isEqualTo("NONE");
    assertThat(t.stagnantItems()).isEmpty();
  }

  @Test
  void getTodayAssignment_予約した案は旧サイクル終了日の翌日から編集後の内容で自動で開始する() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET"); // START(9/7)開始・4週 → 終了日の翌日は10/5
    ProgramCycle started = activeCycle(USER, "TRAINER_MANAGED");
    started.setId(1001L);
    started.setStartDate(START.plusWeeks(4));
    when(programCycleDao.selectActiveByUserId(USER))
        .thenReturn(Optional.of(c))
        .thenReturn(Optional.empty()) // createCycleFromPreset内の退避確認
        .thenReturn(Optional.of(started)); // 再取得
    ProgramCycleProposal proposal = new ProgramCycleProposal();
    proposal.setId(300L);
    proposal.setTrainerUserId(42L);
    proposal.setSourcePresetProgramId(7L);
    proposal.setName("王道4週（佐藤編集）");
    proposal.setTotalWeeks(4);
    when(programCycleProposalDao.selectScheduledByTrainee(USER)).thenReturn(List.of(proposal));
    // 案ごとに持つ中身（トレーナーが開始前に編集した内容）
    ProgramCycleProposalWeek pw = new ProgramCycleProposalWeek();
    pw.setWeekNumber(2);
    pw.setTargetIntensityPct(new BigDecimal("77.5"));
    pw.setDeload(false);
    when(programCycleProposalDao.selectWeeksByProposalId(300L)).thenReturn(List.of(pw));
    doAnswer(
            inv -> {
              ((ProgramCycle) inv.getArgument(0)).setId(1001L);
              return 1;
            })
        .when(programCycleDao)
        .insert(any(ProgramCycle.class));

    // 旧サイクル終了の9日後（10/14）に初めて開いた
    TodayAssignment t = service.getTodayAssignment(USER, START.plusWeeks(4).plusDays(9));

    verify(programCycleDao).completeById(10L);
    ArgumentCaptor<ProgramCycle> cycle = ArgumentCaptor.forClass(ProgramCycle.class);
    verify(programCycleDao).insert(cycle.capture());
    assertThat(cycle.getValue().getStartDate()).isEqualTo(START.plusWeeks(4));
    assertThat(cycle.getValue().getTier()).isEqualTo("TRAINER_MANAGED");
    assertThat(cycle.getValue().getCreatedByTrainerId()).isEqualTo(42L);
    assertThat(cycle.getValue().getName()).isEqualTo("王道4週（佐藤編集）");
    ArgumentCaptor<ProgramCycleWeek> week = ArgumentCaptor.forClass(ProgramCycleWeek.class);
    verify(programCycleDao).insertWeek(week.capture());
    assertThat(week.getValue().getTargetIntensityPct()).isEqualByComparingTo("77.5");
    verify(presetProgramDao, never()).selectWeeksByPresetId(anyLong());
    verify(programCycleProposalDao).markStarted(eq(300L), any(), any());
    verify(programCycleDao).markRenewDecidedById(eq(10L), any());
    // 予約した案そのものの自動開始は「本人の切り替え」ではないので取り消し処理は走らない
    verify(programCycleProposalDao, never()).cancelScheduledBySwitch(anyLong());
    // 開いた時点で途中の週（2週目）から始まり、3択は出ない
    assertThat(t.cycleCompleted()).isFalse();
    assertThat(t.weekNumber()).isEqualTo(2);
  }

  @Test
  void getTodayAssignment_予約が無ければ従来どおり3択を出す() {
    ProgramCycle c = activeCycle(USER, "BEGINNER_PRESET");
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.of(c));
    when(programCycleProposalDao.selectScheduledByTrainee(USER)).thenReturn(List.of());

    assertThat(service.getTodayAssignment(USER, START.plusWeeks(4)).cycleCompleted()).isTrue();
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void renewCycle_GO_FREEFORMは新しいサイクルを作らない() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = activeCycle(USER, "BEGINNER_PRESET");
    done.setStatus("COMPLETED");
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));

    assertThat(service.renewCycle(USER, RenewChoice.GO_FREEFORM, null)).isEmpty();
    verify(programCycleDao, never()).insert(any());
    verify(programCycleProposalDao, never()).cancelScheduledBySwitch(anyLong());
    verify(programCycleDao).markRenewDecidedById(eq(10L), any());
  }

  @Test
  void getTodayAssignment_GO_FREEFORMで選択済みなら3択を再表示しない() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = activeCycle(USER, "BEGINNER_PRESET");
    done.setStatus("COMPLETED");
    done.setRenewDecidedAt(java.time.LocalDateTime.of(2026, 10, 5, 9, 0));
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));

    TodayAssignment t = service.getTodayAssignment(USER, START.plusWeeks(5));

    assertThat(t.cycleCompleted()).isFalse();
    assertThat(t.hasActiveCycle()).isFalse();
  }

  @Test
  void getTodayAssignment_COMPLETEDで未選択なら3択を表示する() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = activeCycle(USER, "BEGINNER_PRESET");
    done.setStatus("COMPLETED");
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));

    assertThat(service.getTodayAssignment(USER, START.plusWeeks(5)).cycleCompleted()).isTrue();
  }

  @Test
  void renewCycle_選択済みのサイクルに再度選択すると409() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = activeCycle(USER, "BEGINNER_PRESET");
    done.setStatus("COMPLETED");
    done.setRenewDecidedAt(java.time.LocalDateTime.of(2026, 10, 5, 9, 0));
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));

    assertThatThrownBy(() -> service.renewCycle(USER, RenewChoice.GO_FREEFORM, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(programCycleDao, never()).markRenewDecidedById(anyLong(), any());
  }

  private ProgramCycle completedCustomCycle() {
    ProgramCycle done = activeCycle(USER, "INTERMEDIATE_CUSTOM");
    done.setStatus("COMPLETED");
    done.setName("自作3週");
    done.setTotalWeeks(3);
    ProgramCycleWeek w = new ProgramCycleWeek();
    w.setWeekNumber(3);
    w.setTargetIntensityPct(new BigDecimal("55.0"));
    w.setDeload(true);
    when(programCycleDao.selectWeeksByCycleId(10L)).thenReturn(List.of(w));
    ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
    d.setId(300L);
    d.setWeekNumber(1);
    d.setDayOfWeek("TUE");
    d.setPartCode("LEG");
    when(programCycleDao.selectDayTemplatesByCycleId(10L)).thenReturn(List.of(d));
    ProgramCycleDayTemplateItem edited = new ProgramCycleDayTemplateItem();
    edited.setDayTemplateId(300L);
    edited.setItemName("スクワット");
    edited.setDisplayOrder(1);
    edited.setTargetSets(5);
    when(programCycleDao.selectItemsByCycleId(10L)).thenReturn(List.of(edited));
    return done;
  }

  @Test
  void renewCycle_REPEAT_SAMEは直前のサイクルの内容とtierをコピーする() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = completedCustomCycle();
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));
    doAnswer(
            inv -> {
              ((ProgramCycle) inv.getArgument(0)).setId(77L);
              return 1;
            })
        .when(programCycleDao)
        .insert(any(ProgramCycle.class));
    doAnswer(
            inv -> {
              ((ProgramCycleDayTemplate) inv.getArgument(0)).setId(700L);
              return 1;
            })
        .when(programCycleDao)
        .insertDayTemplate(any(ProgramCycleDayTemplate.class));
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(120.0);
    pr.setMaxReps(1);
    when(personalRecordService.getByUserIdAndItem(USER, "スクワット")).thenReturn(Optional.of(pr));

    assertThat(service.renewCycle(USER, RenewChoice.REPEAT_SAME, null)).contains(77L);

    ArgumentCaptor<ProgramCycle> cycle = ArgumentCaptor.forClass(ProgramCycle.class);
    verify(programCycleDao).insert(cycle.capture());
    assertThat(cycle.getValue().getTier()).isEqualTo("INTERMEDIATE_CUSTOM");
    assertThat(cycle.getValue().getName()).isEqualTo("自作3週");
    assertThat(cycle.getValue().getTotalWeeks()).isEqualTo(3);
    assertThat(cycle.getValue().getStatus()).isEqualTo("ACTIVE");
    ArgumentCaptor<ProgramCycleWeek> week = ArgumentCaptor.forClass(ProgramCycleWeek.class);
    verify(programCycleDao).insertWeek(week.capture());
    assertThat(week.getValue().getCycleId()).isEqualTo(77L);
    assertThat(week.getValue().getDeload()).isTrue();
    ArgumentCaptor<ProgramCycleDayTemplateItem> item =
        ArgumentCaptor.forClass(ProgramCycleDayTemplateItem.class);
    verify(programCycleDao).insertDayTemplateItem(item.capture());
    assertThat(item.getValue().getDayTemplateId()).isEqualTo(700L);
    assertThat(item.getValue().getItemName()).isEqualTo("スクワット");
    assertThat(item.getValue().getTargetSets()).isEqualTo(5); // 編集結果を引き継ぐ
    // 基準1RMは新サイクル開始時点のPRで取り直す
    ArgumentCaptor<ProgramCycleItemBaseline> baseline =
        ArgumentCaptor.forClass(ProgramCycleItemBaseline.class);
    verify(programCycleDao).insertItemBaseline(baseline.capture());
    assertThat(baseline.getValue().getCycleId()).isEqualTo(77L);
    assertThat(baseline.getValue().getBaselineOneRm()).isEqualByComparingTo("120.0");
    verify(programCycleDao).markRenewDecidedById(eq(10L), any());
    verify(presetProgramDao, never()).selectById(anyLong());
    verify(programCycleProposalDao).cancelScheduledBySwitch(USER);
  }

  @Test
  void renewCycle_REPEAT_SAMEはトレーナー作成サイクルのtierと作成者も引き継ぐ() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    ProgramCycle done = completedCustomCycle();
    done.setTier("TRAINER_MANAGED");
    done.setCreatedByTrainerId(42L);
    done.setSourcePresetId(7L);
    when(programCycleDao.selectRecentByUserId(USER, 1)).thenReturn(List.of(done));
    doAnswer(
            inv -> {
              ((ProgramCycle) inv.getArgument(0)).setId(78L);
              return 1;
            })
        .when(programCycleDao)
        .insert(any(ProgramCycle.class));

    assertThat(service.renewCycle(USER, RenewChoice.REPEAT_SAME, null)).contains(78L);

    ArgumentCaptor<ProgramCycle> cycle = ArgumentCaptor.forClass(ProgramCycle.class);
    verify(programCycleDao).insert(cycle.capture());
    assertThat(cycle.getValue().getTier()).isEqualTo("TRAINER_MANAGED");
    assertThat(cycle.getValue().getCreatedByTrainerId()).isEqualTo(42L);
    assertThat(cycle.getValue().getSourcePresetId()).isEqualTo(7L);
  }

  private static PeriodizationViews.CustomCycleInput customInput(int totalWeeks, String pct) {
    List<PeriodizationViews.WeekInput> weeks = new ArrayList<>();
    for (int i = 1; i <= totalWeeks; i++) {
      weeks.add(new PeriodizationViews.WeekInput(i, new BigDecimal(pct), i == totalWeeks));
    }
    return new PeriodizationViews.CustomCycleInput(
        " 自作プログラム ",
        totalWeeks,
        weeks,
        List.of(
            new PeriodizationViews.DayInput(
                1, "mon", "CHEST", List.of(new ItemInput("ベンチプレス", 4)))));
  }

  private void stubParts() {
    com.example.traning.entity.TrainingMaster chest =
        new com.example.traning.entity.TrainingMaster();
    chest.setPartCode("CHEST");
    when(trainingMasterDao.selectAllParts()).thenReturn(List.of(chest));
  }

  @Test
  void createCustomCycle_白紙から組んだサイクルはINTERMEDIATE_CUSTOM() {
    stubParts();
    when(trainingMasterDao.selectByItemName("ベンチプレス"))
        .thenReturn(Optional.of(new TrainingItemMaster()));
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    doAnswer(
            inv -> {
              ((ProgramCycle) inv.getArgument(0)).setId(55L);
              return 1;
            })
        .when(programCycleDao)
        .insert(any(ProgramCycle.class));

    PeriodizationViews.CustomCycleResult result =
        service.createCustomCycle(USER, customInput(3, "72.5"), START);

    assertThat(result.cycleId()).isEqualTo(55L);
    verify(programCycleProposalDao).cancelScheduledBySwitch(USER);
    assertThat(result.warnings()).isEmpty();
    ArgumentCaptor<ProgramCycle> cycle = ArgumentCaptor.forClass(ProgramCycle.class);
    verify(programCycleDao).insert(cycle.capture());
    assertThat(cycle.getValue().getTier()).isEqualTo("INTERMEDIATE_CUSTOM");
    assertThat(cycle.getValue().getName()).isEqualTo("自作プログラム");
    assertThat(cycle.getValue().getSourcePresetId()).isNull();
    verify(programCycleDao, org.mockito.Mockito.times(3)).insertWeek(any());
    ArgumentCaptor<ProgramCycleDayTemplate> day =
        ArgumentCaptor.forClass(ProgramCycleDayTemplate.class);
    verify(programCycleDao).insertDayTemplate(day.capture());
    assertThat(day.getValue().getDayOfWeek()).isEqualTo("MON");
  }

  @Test
  void createCustomCycle_全週の強度が揃っていなければ400() {
    PeriodizationViews.CustomCycleInput in = customInput(3, "70");
    PeriodizationViews.CustomCycleInput missing =
        new PeriodizationViews.CustomCycleInput(in.name(), 4, in.weeks(), in.days());
    assertThatThrownBy(() -> service.createCustomCycle(USER, missing, START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void createCustomCycle_週数1や13は400() {
    assertThatThrownBy(() -> service.createCustomCycle(USER, customInput(1, "70"), START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    assertThatThrownBy(() -> service.createCustomCycle(USER, customInput(13, "70"), START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
    verify(programCycleDao, never()).insert(any());
  }

  @Test
  void createCustomCycle_強度100超は400() {
    assertThatThrownBy(() -> service.createCustomCycle(USER, customInput(2, "100.5"), START))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void renewCycle_終了したサイクルが無ければ409() {
    when(programCycleDao.selectActiveByUserId(USER)).thenReturn(Optional.empty());
    when(programCycleDao.selectRecentByUserId(eq(USER), eq(1))).thenReturn(List.of());
    assertThatThrownBy(() -> service.renewCycle(USER, RenewChoice.GO_FREEFORM, null))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }
}
