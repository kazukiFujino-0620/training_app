package com.example.traning.restpreference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 機能見直し-1-#1: 休憩タイマー自動算出ロジックの単体テスト。
 *
 * <p>複合/単関節2区分 × 推定1RM比率3段階の組み合わせ、個人上書きの優先、PR未登録時のフォールバックを検証する。
 */
@ExtendWith(MockitoExtension.class)
class RestIntervalCalculationServiceTest {

  private static final Long USER_ID = 1L;
  private static final String ITEM_NAME = "ベンチプレス";

  @Mock private RestPreferenceService restPreferenceService;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private PersonalRecordService personalRecordService;

  // Epley式の実装をそのまま使う（本物のサービス。副作用が無いためモック化不要）。
  private final OneRmPredictionService oneRmPredictionService = new OneRmPredictionService();

  private RestIntervalCalculationService service;

  @BeforeEach
  void setUp() {
    service =
        new RestIntervalCalculationService(
            restPreferenceService,
            oneRmPredictionService,
            trainingMasterDao,
            personalRecordService);
    when(restPreferenceService.find(eq(USER_ID), eq(ITEM_NAME))).thenReturn(Optional.empty());
  }

  private void mockIsCompound(boolean isCompound) {
    TrainingItemMaster master = new TrainingItemMaster();
    master.setIsCompound(isCompound);
    when(trainingMasterDao.selectByItemName(ITEM_NAME)).thenReturn(Optional.of(master));
  }

  private void mockPersonalRecord(double maxWeight, int maxReps) {
    PersonalRecord pr = new PersonalRecord();
    pr.setMaxWeight(maxWeight);
    pr.setMaxReps(maxReps);
    when(personalRecordService.getByUserIdAndItem(USER_ID, ITEM_NAME)).thenReturn(Optional.of(pr));
  }

  @Test
  void 個人上書き設定があれば最優先で返す() {
    UserItemRestPreference pref = new UserItemRestPreference();
    pref.setRestSeconds(999);
    when(restPreferenceService.find(USER_ID, ITEM_NAME)).thenReturn(Optional.of(pref));

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 100, 5);

    assertThat(result).isEqualTo(999);
    verifyNoInteractions(personalRecordService);
  }

  @Test
  void 複合種目_強度85パーセント以上は180秒() {
    mockIsCompound(true);
    // PR: 100kg x 1回 -> 推定1RM=100kg。今回90kg -> ratio=0.9 (>=0.85)
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 90, 5);

    assertThat(result).isEqualTo(180);
  }

  @Test
  void 複合種目_強度65から85パーセントは120秒() {
    mockIsCompound(true);
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 70, 5);

    assertThat(result).isEqualTo(120);
  }

  @Test
  void 複合種目_強度65パーセント未満は90秒() {
    mockIsCompound(true);
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 50, 5);

    assertThat(result).isEqualTo(90);
  }

  @Test
  void 単関節種目_強度85パーセント以上は150秒() {
    mockIsCompound(false);
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 90, 5);

    assertThat(result).isEqualTo(150);
  }

  @Test
  void 単関節種目_強度65から85パーセントは90秒() {
    mockIsCompound(false);
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 70, 5);

    assertThat(result).isEqualTo(90);
  }

  @Test
  void 単関節種目_強度65パーセント未満は60秒() {
    mockIsCompound(false);
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 50, 5);

    assertThat(result).isEqualTo(60);
  }

  @Test
  void PR未登録時は当日セット自体を暫定PRとして推定1RMを算出する() {
    mockIsCompound(true);
    when(personalRecordService.getByUserIdAndItem(USER_ID, ITEM_NAME)).thenReturn(Optional.empty());

    // 初回セット: weight=100, reps=1 -> Epley式でreps<=1はそのままweightが1RM扱い -> ratio=1.0 (>=0.85)
    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 100, 1);

    assertThat(result).isEqualTo(180);
  }

  @Test
  void is_compound未設定の種目マスタはデフォルトで複合種目として扱う() {
    when(trainingMasterDao.selectByItemName(ITEM_NAME)).thenReturn(Optional.empty());
    mockPersonalRecord(100, 1);

    int result = service.resolveIntervalSeconds(USER_ID, ITEM_NAME, 90, 5);

    assertThat(result).isEqualTo(180);
  }
}
