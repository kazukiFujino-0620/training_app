package com.example.traning.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

/** 機能見直し-1-#5: 睡眠の手動入力（時間のみ・1日1行の上書き）と過去平均の検証。 */
@ExtendWith(MockitoExtension.class)
class HealthSleepServiceTest {

  @Mock private HealthSleepDao dao;

  private HealthSleepService service;

  private static final Long USER_ID = 1L;
  private static final LocalDate DATE = LocalDate.of(2026, 10, 10);

  @BeforeEach
  void setUp() {
    service = new HealthSleepService(dao);
  }

  @Test
  void 手動入力は睡眠時間のみをMANUALで登録し寝た起きた時刻は空にする() {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, 390, false);

    ArgumentCaptor<HealthSleep> captor = ArgumentCaptor.forClass(HealthSleep.class);
    verify(dao).insert(captor.capture());
    HealthSleep s = captor.getValue();
    assertThat(s.sleepDate).isEqualTo(DATE);
    assertThat(s.durationMinutes).isEqualTo(390);
    assertThat(s.source).isEqualTo("MANUAL");
    assertThat(s.startTime).isNull();
    assertThat(s.endTime).isNull();
  }

  @Test
  void 手動入力は同日の自動同期データを上書きし時刻を空にする() {
    HealthSleep existing = new HealthSleep();
    existing.id = 3L;
    existing.startTime = LocalDateTime.of(2026, 10, 9, 23, 0);
    existing.endTime = LocalDateTime.of(2026, 10, 10, 6, 0);
    existing.durationMinutes = 420;
    existing.source = "HEALTH_CONNECT";
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.of(existing));

    service.saveManual(USER_ID, DATE, 400, false);

    verify(dao).update(existing);
    verify(dao, never()).insert(any());
    assertThat(existing.durationMinutes).isEqualTo(400);
    assertThat(existing.source).isEqualTo("MANUAL");
    assertThat(existing.startTime).isNull();
    assertThat(existing.endTime).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 24 * 60 + 1})
  void 入力エラー範囲の睡眠時間は確認済みでも保存しない(int minutes) {
    assertThatThrownBy(() -> service.saveManual(USER_ID, DATE, minutes, true))
        .isInstanceOfSatisfying(
            HealthInputRejectedException.class,
            e -> assertThat(e.isConfirmationRequired()).isFalse());
    verify(dao, never()).insert(any());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 165, 14 * 60 + 15, 24 * 60})
  void 確認範囲の睡眠時間は未確認なら確認を求める(int minutes) {
    assertThatThrownBy(() -> service.saveManual(USER_ID, DATE, minutes, false))
        .isInstanceOfSatisfying(
            HealthInputRejectedException.class,
            e -> assertThat(e.isConfirmationRequired()).isTrue());
    verify(dao, never()).insert(any());
  }

  @Test
  void 徹夜の0分は確認済みなら記録として保存される() {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, 0, true);

    ArgumentCaptor<HealthSleep> captor = ArgumentCaptor.forClass(HealthSleep.class);
    verify(dao).insert(captor.capture());
    assertThat(captor.getValue().durationMinutes).isZero();
  }

  @ParameterizedTest
  @ValueSource(ints = {3 * 60, 14 * 60})
  void 通常範囲の境界値は確認なしで保存する(int minutes) {
    when(dao.selectByUserIdAndDate(USER_ID, DATE)).thenReturn(Optional.empty());

    service.saveManual(USER_ID, DATE, minutes, false);

    verify(dao).insert(any());
  }

  @Test
  void 過去平均は前日までの4週間の記録から算出する() {
    HealthSleep a = new HealthSleep();
    a.durationMinutes = 420;
    HealthSleep b = new HealthSleep();
    b.durationMinutes = 480;
    HealthSleep c = new HealthSleep();
    c.durationMinutes = 400;
    when(dao.selectByUserIdAndDateRange(USER_ID, DATE.minusWeeks(4), DATE.minusDays(1)))
        .thenReturn(List.of(a, b, c));

    assertThat(service.getAverageDurationMinutes(USER_ID, HealthSleepService.AVERAGE_WEEKS, DATE))
        .contains(new HealthAverage(433, 3)); // 433.33
  }
}
