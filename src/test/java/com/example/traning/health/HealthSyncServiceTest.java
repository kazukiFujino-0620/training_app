package com.example.traning.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.body.BodyMeasurement;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.mobile.dto.HealthSummaryResponse;
import com.example.traning.mobile.dto.HealthSyncRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ita3-1（ヘルスケア連動対応）: {@link HealthSyncService}の同期（insert/update判定）・
 * サマリー取得ロジックの検証。DAO/BodyMeasurementServiceはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class HealthSyncServiceTest {

  @Mock private BodyMeasurementService bodyMeasurementService;
  @Mock private HealthStepsDao healthStepsDao;
  @Mock private HealthHeartRateDao healthHeartRateDao;
  @Mock private HealthCaloriesDao healthCaloriesDao;
  @Mock private HealthSleepDao healthSleepDao;

  private HealthSyncService service;

  private static final Long USER_ID = 1L;
  private static final LocalDate DATE = LocalDate.of(2026, 8, 19);

  @BeforeEach
  void setUp() {
    service =
        new HealthSyncService(
            bodyMeasurementService,
            healthStepsDao,
            healthHeartRateDao,
            healthCaloriesDao,
            healthSleepDao);
  }

  private HealthSyncRequest emptyRequest() {
    HealthSyncRequest req = new HealthSyncRequest();
    req.setSource("HEALTHKIT");
    return req;
  }

  @Test
  void 体重レコードはBodyMeasurementServiceのsyncFromHealthPlatformに委譲される() {
    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.WeightRecord w = new HealthSyncRequest.WeightRecord();
    w.setDate(DATE);
    w.setWeightKg(70.5);
    w.setBodyFatPct(18.2);
    req.setWeight(List.of(w));

    int count = service.sync(USER_ID, req);

    assertThat(count).isEqualTo(1);
    verify(bodyMeasurementService).syncFromHealthPlatform(USER_ID, DATE, 70.5, 18.2, "HEALTHKIT");
  }

  @Test
  void 歩数の新規レコードはinsertされる() {
    when(healthStepsDao.selectByUserIdAndDateAndSource(USER_ID, DATE, "HEALTHKIT"))
        .thenReturn(Optional.empty());

    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.StepsRecord s = new HealthSyncRequest.StepsRecord();
    s.setDate(DATE);
    s.setStepCount(8000);
    req.setSteps(List.of(s));

    int count = service.sync(USER_ID, req);

    assertThat(count).isEqualTo(1);
    ArgumentCaptor<HealthSteps> captor = ArgumentCaptor.forClass(HealthSteps.class);
    verify(healthStepsDao).insert(captor.capture());
    verify(healthStepsDao, never()).update(any());
    HealthSteps inserted = captor.getValue();
    assertThat(inserted.userId).isEqualTo(USER_ID);
    assertThat(inserted.recordDate).isEqualTo(DATE);
    assertThat(inserted.stepCount).isEqualTo(8000);
    assertThat(inserted.source).isEqualTo("HEALTHKIT");
  }

  @Test
  void 歩数の既存レコードはinsertされずupdateされる() {
    HealthSteps existing = new HealthSteps();
    existing.id = 99L;
    existing.userId = USER_ID;
    existing.recordDate = DATE;
    existing.stepCount = 5000;
    existing.source = "HEALTHKIT";
    when(healthStepsDao.selectByUserIdAndDateAndSource(USER_ID, DATE, "HEALTHKIT"))
        .thenReturn(Optional.of(existing));

    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.StepsRecord s = new HealthSyncRequest.StepsRecord();
    s.setDate(DATE);
    s.setStepCount(9500);
    req.setSteps(List.of(s));

    service.sync(USER_ID, req);

    verify(healthStepsDao, never()).insert(any());
    ArgumentCaptor<HealthSteps> captor = ArgumentCaptor.forClass(HealthSteps.class);
    verify(healthStepsDao).update(captor.capture());
    // 既存エンティティ(id=99)をそのまま更新している（新規オブジェクトを作っていない）ことを確認
    assertThat(captor.getValue().id).isEqualTo(99L);
    assertThat(captor.getValue().stepCount).isEqualTo(9500);
  }

  @Test
  void 心拍数の新規レコードはinsertされ最小最大平均が正しく設定される() {
    when(healthHeartRateDao.selectByUserIdAndDateAndSource(USER_ID, DATE, "HEALTH_CONNECT"))
        .thenReturn(Optional.empty());

    HealthSyncRequest req = emptyRequest();
    req.setSource("HEALTH_CONNECT");
    HealthSyncRequest.HeartRateRecord h = new HealthSyncRequest.HeartRateRecord();
    h.setDate(DATE);
    h.setAvgBpm(72);
    h.setMinBpm(58);
    h.setMaxBpm(140);
    req.setHeartRate(List.of(h));

    service.sync(USER_ID, req);

    ArgumentCaptor<HealthHeartRate> captor = ArgumentCaptor.forClass(HealthHeartRate.class);
    verify(healthHeartRateDao).insert(captor.capture());
    assertThat(captor.getValue().avgBpm).isEqualTo(72);
    assertThat(captor.getValue().minBpm).isEqualTo(58);
    assertThat(captor.getValue().maxBpm).isEqualTo(140);
    assertThat(captor.getValue().source).isEqualTo("HEALTH_CONNECT");
  }

  @Test
  void 消費カロリーの新規レコードはinsertされる() {
    when(healthCaloriesDao.selectByUserIdAndDateAndSource(USER_ID, DATE, "HEALTHKIT"))
        .thenReturn(Optional.empty());

    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.CaloriesRecord c = new HealthSyncRequest.CaloriesRecord();
    c.setDate(DATE);
    c.setActiveCalories(320.5);
    c.setTotalCalories(2100.0);
    req.setCalories(List.of(c));

    service.sync(USER_ID, req);

    ArgumentCaptor<HealthCalories> captor = ArgumentCaptor.forClass(HealthCalories.class);
    verify(healthCaloriesDao).insert(captor.capture());
    assertThat(captor.getValue().activeCalories).isEqualTo(320.5);
    assertThat(captor.getValue().totalCalories).isEqualTo(2100.0);
  }

  @Test
  void 睡眠の新規レコードはinsertされる() {
    when(healthSleepDao.selectByUserIdAndDateAndSource(USER_ID, DATE, "HEALTHKIT"))
        .thenReturn(Optional.empty());

    LocalDateTime start = LocalDateTime.of(2026, 8, 18, 23, 30);
    LocalDateTime end = LocalDateTime.of(2026, 8, 19, 6, 45);

    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.SleepRecord sl = new HealthSyncRequest.SleepRecord();
    sl.setDate(DATE);
    sl.setStartTime(start);
    sl.setEndTime(end);
    sl.setDurationMinutes(435);
    req.setSleep(List.of(sl));

    service.sync(USER_ID, req);

    ArgumentCaptor<HealthSleep> captor = ArgumentCaptor.forClass(HealthSleep.class);
    verify(healthSleepDao).insert(captor.capture());
    HealthSleep inserted = captor.getValue();
    assertThat(inserted.sleepDate).isEqualTo(DATE);
    assertThat(inserted.startTime).isEqualTo(start);
    assertThat(inserted.endTime).isEqualTo(end);
    assertThat(inserted.durationMinutes).isEqualTo(435);
  }

  @Test
  void 全項目を含むリクエストは件数が正しく合算される() {
    when(healthStepsDao.selectByUserIdAndDateAndSource(eq(USER_ID), any(), eq("HEALTHKIT")))
        .thenReturn(Optional.empty());
    when(healthHeartRateDao.selectByUserIdAndDateAndSource(eq(USER_ID), any(), eq("HEALTHKIT")))
        .thenReturn(Optional.empty());
    when(healthCaloriesDao.selectByUserIdAndDateAndSource(eq(USER_ID), any(), eq("HEALTHKIT")))
        .thenReturn(Optional.empty());
    when(healthSleepDao.selectByUserIdAndDateAndSource(eq(USER_ID), any(), eq("HEALTHKIT")))
        .thenReturn(Optional.empty());

    HealthSyncRequest req = emptyRequest();
    HealthSyncRequest.WeightRecord w = new HealthSyncRequest.WeightRecord();
    w.setDate(DATE);
    w.setWeightKg(70.0);
    req.setWeight(List.of(w));

    HealthSyncRequest.StepsRecord s = new HealthSyncRequest.StepsRecord();
    s.setDate(DATE);
    s.setStepCount(1000);
    req.setSteps(List.of(s));

    HealthSyncRequest.HeartRateRecord h = new HealthSyncRequest.HeartRateRecord();
    h.setDate(DATE);
    h.setAvgBpm(70);
    req.setHeartRate(List.of(h));

    HealthSyncRequest.CaloriesRecord c = new HealthSyncRequest.CaloriesRecord();
    c.setDate(DATE);
    c.setTotalCalories(2000.0);
    req.setCalories(List.of(c));

    HealthSyncRequest.SleepRecord sl = new HealthSyncRequest.SleepRecord();
    sl.setDate(DATE);
    sl.setStartTime(LocalDateTime.of(2026, 8, 18, 23, 0));
    sl.setEndTime(LocalDateTime.of(2026, 8, 19, 7, 0));
    sl.setDurationMinutes(480);
    req.setSleep(List.of(sl));

    int count = service.sync(USER_ID, req);

    assertThat(count).isEqualTo(5);
  }

  @Test
  void 空のリクエストは件数0でDAOを一切呼び出さない() {
    HealthSyncRequest req = emptyRequest();

    int count = service.sync(USER_ID, req);

    assertThat(count).isEqualTo(0);
    verify(healthStepsDao, never()).insert(any());
    verify(healthStepsDao, never()).update(any());
    verify(healthHeartRateDao, never()).insert(any());
    verify(healthCaloriesDao, never()).insert(any());
    verify(healthSleepDao, never()).insert(any());
  }

  @Test
  void getSummaryは各指標の最新値をまとめて返す() {
    BodyMeasurement bm = new BodyMeasurement();
    bm.measuredDate = DATE;
    bm.weightKg = 70.0;
    bm.bodyFatPct = 18.0;
    bm.source = "HEALTHKIT";
    when(bodyMeasurementService.getLatest(USER_ID)).thenReturn(Optional.of(bm));

    HealthSteps steps = new HealthSteps();
    steps.recordDate = DATE;
    steps.stepCount = 8500;
    steps.source = "HEALTHKIT";
    when(healthStepsDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.of(steps));

    HealthHeartRate hr = new HealthHeartRate();
    hr.recordDate = DATE;
    hr.avgBpm = 65;
    hr.minBpm = 50;
    hr.maxBpm = 130;
    hr.source = "HEALTHKIT";
    when(healthHeartRateDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.of(hr));

    HealthCalories cal = new HealthCalories();
    cal.recordDate = DATE;
    cal.activeCalories = 300.0;
    cal.totalCalories = 2200.0;
    cal.source = "HEALTHKIT";
    when(healthCaloriesDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.of(cal));

    HealthSleep sleep = new HealthSleep();
    sleep.sleepDate = DATE;
    sleep.startTime = LocalDateTime.of(2026, 8, 18, 23, 0);
    sleep.endTime = LocalDateTime.of(2026, 8, 19, 7, 0);
    sleep.durationMinutes = 480;
    sleep.source = "HEALTHKIT";
    when(healthSleepDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.of(sleep));

    HealthSummaryResponse response = service.getSummary(USER_ID);

    assertThat(response.getWeight().getWeightKg()).isEqualTo(70.0);
    assertThat(response.getWeight().getBodyFatPct()).isEqualTo(18.0);
    assertThat(response.getSteps().getStepCount()).isEqualTo(8500);
    assertThat(response.getHeartRate().getAvgBpm()).isEqualTo(65);
    assertThat(response.getHeartRate().getMinBpm()).isEqualTo(50);
    assertThat(response.getHeartRate().getMaxBpm()).isEqualTo(130);
    assertThat(response.getCalories().getActiveCalories()).isEqualTo(300.0);
    assertThat(response.getCalories().getTotalCalories()).isEqualTo(2200.0);
    assertThat(response.getSleep().getDurationMinutes()).isEqualTo(480);
  }

  @Test
  void getSummaryはデータが無い項目をnullのまま返す() {
    when(bodyMeasurementService.getLatest(USER_ID)).thenReturn(Optional.empty());
    when(healthStepsDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.empty());
    when(healthHeartRateDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.empty());
    when(healthCaloriesDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.empty());
    when(healthSleepDao.selectLatestByUserId(USER_ID)).thenReturn(Optional.empty());

    HealthSummaryResponse response = service.getSummary(USER_ID);

    assertThat(response.getWeight()).isNull();
    assertThat(response.getSteps()).isNull();
    assertThat(response.getHeartRate()).isNull();
    assertThat(response.getCalories()).isNull();
    assertThat(response.getSleep()).isNull();
  }
}
