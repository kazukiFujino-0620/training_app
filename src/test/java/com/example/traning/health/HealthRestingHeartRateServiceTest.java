package com.example.traning.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 機能見直し-1-#5: 安静時心拍の手動入力・同期（1日1行の上書き）と過去平均の検証。 */
@ExtendWith(MockitoExtension.class)
class HealthRestingHeartRateServiceTest {

  @Mock private HealthRestingHeartRateDao dao;

  private HealthRestingHeartRateService service;

  private static final Long USER_ID = 1L;
  private static final LocalDate DATE = LocalDate.of(2026, 10, 10);

  @BeforeEach
  void setUp() {
    service = new HealthRestingHeartRateService(dao);
  }

  @Test
  void 手動入力で同日の記録が無ければMANUALでinsertされる() {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, 58, false);

    ArgumentCaptor<HealthRestingHeartRate> captor =
        ArgumentCaptor.forClass(HealthRestingHeartRate.class);
    verify(dao).insert(captor.capture());
    assertThat(captor.getValue().userId).isEqualTo(USER_ID);
    assertThat(captor.getValue().recordDate).isEqualTo(DATE);
    assertThat(captor.getValue().restingBpm).isEqualTo(58);
    assertThat(captor.getValue().source).isEqualTo("MANUAL");
  }

  @Test
  void 手動入力は同日の自動同期データを上書きしsourceをMANUALにする() {
    HealthRestingHeartRate existing = new HealthRestingHeartRate();
    existing.id = 5L;
    existing.restingBpm = 60;
    existing.source = "HEALTHKIT";
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.of(existing));

    service.saveManual(USER_ID, DATE, 55, false);

    verify(dao).update(existing);
    verify(dao, never()).insert(any());
    assertThat(existing.restingBpm).isEqualTo(55);
    assertThat(existing.source).isEqualTo("MANUAL");
  }

  @Test
  void 過去平均は前日までの4週間の記録から四捨五入で算出し日数も返す() {
    HealthRestingHeartRate a = new HealthRestingHeartRate();
    a.restingBpm = 55;
    HealthRestingHeartRate b = new HealthRestingHeartRate();
    b.restingBpm = 56;
    when(dao.selectByUserIdAndDateRange(USER_ID, DATE.minusWeeks(4), DATE.minusDays(1)))
        .thenReturn(List.of(a, b));

    Optional<HealthAverage> avg = service.getAverage(USER_ID, 4, DATE);

    assertThat(avg).contains(new HealthAverage(56, 2)); // 55.5 -> 56
  }

  @ParameterizedTest
  @ValueSource(ints = {24, 151, 0, 300})
  void 入力エラー範囲の値は確認済みでも保存しない(int bpm) {
    assertThatThrownBy(() -> service.saveManual(USER_ID, DATE, bpm, true))
        .isInstanceOfSatisfying(
            HealthInputRejectedException.class,
            e -> assertThat(e.isConfirmationRequired()).isFalse());
    verify(dao, never()).insert(any());
    verify(dao, never()).update(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {25, 39, 101, 150})
  void 確認範囲の値は未確認なら保存せず確認を求める(int bpm) {
    assertThatThrownBy(() -> service.saveManual(USER_ID, DATE, bpm, false))
        .isInstanceOfSatisfying(
            HealthInputRejectedException.class,
            e -> {
              assertThat(e.isConfirmationRequired()).isTrue();
              assertThat(e.getMessage()).isEqualTo(HealthInputLimits.CONFIRM_MESSAGE);
            });
    verify(dao, never()).insert(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {25, 39, 101, 150})
  void 確認範囲の値は確認済みなら保存する(int bpm) {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, bpm, true);

    verify(dao).insert(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {40, 100})
  void 通常範囲の境界値は確認なしで保存する(int bpm) {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, bpm, false);

    verify(dao).insert(any());
  }

  @Test
  void 自動同期は確認範囲や入力エラー範囲の値でもそのまま保存する() {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.syncFromHealthPlatform(USER_ID, DATE, 160, "HEALTHKIT");

    verify(dao).insert(any());
  }

  @Test
  void 平均の計算から入力エラー範囲の値を除外する() {
    HealthRestingHeartRate ok1 = new HealthRestingHeartRate();
    ok1.restingBpm = 50;
    HealthRestingHeartRate ok2 = new HealthRestingHeartRate();
    ok2.restingBpm = 150;
    HealthRestingHeartRate low = new HealthRestingHeartRate();
    low.restingBpm = 20;
    HealthRestingHeartRate high = new HealthRestingHeartRate();
    high.restingBpm = 180;
    when(dao.selectByUserIdAndDateRange(USER_ID, DATE.minusWeeks(4), DATE.minusDays(1)))
        .thenReturn(List.of(ok1, low, ok2, high));

    assertThat(service.getAverage(USER_ID, 4, DATE)).contains(new HealthAverage(100, 2));
  }

  @Test
  void 過去の記録が無ければ平均はempty() {
    when(dao.selectByUserIdAndDateRange(USER_ID, DATE.minusWeeks(4), DATE.minusDays(1)))
        .thenReturn(List.of());

    assertThat(service.getAverage(USER_ID, 4, DATE)).isEmpty();
  }
}
