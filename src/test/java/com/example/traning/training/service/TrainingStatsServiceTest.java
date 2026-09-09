package com.example.traning.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.weekly.WeeklyProgram;
import com.example.traning.weekly.WeeklyProgramService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ita7-2: Web版 {@code MenuController.menu()} から切り出した統計バー算出ロジック（
 * {@link TrainingStatsService}）の検証。DAO/WeeklyProgramServiceはMockitoでモックし、DBには依存しない。
 * 切り出し前のMenuControllerの挙動（R1〜R3・今日の予定）と完全に同一の結果になることを保証する。
 */
@ExtendWith(MockitoExtension.class)
class TrainingStatsServiceTest {

  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private WeeklyProgramService weeklyProgramService;

  private TrainingStatsService service;

  private static final Long USER_ID = 1L;
  // 2026-09-10は木曜日（週の起点=直近の月曜は2026-09-07）
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

  @BeforeEach
  void setUp() {
    service = new TrainingStatsService(trainingDao, trainingDetailDao, weeklyProgramService);
  }

  @Test
  void 今月の回数はDAOの値をそのまま返す() {
    when(trainingDao.countByUserIdAndMonth(USER_ID, 2026, 9)).thenReturn(25);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(null);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.monthlyCount()).isEqualTo(25);
  }

  @Test
  void 今週実施済みの部位はdoneがtrueになり順序はCHEST_BACK_SHOULDER_ARM_LEGの固定順() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of("CHEST", "BACK", "SHOULDER"));
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(null);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.weekParts()).hasSize(5);
    assertThat(stats.weekParts().get(0).name()).isEqualTo("胸");
    assertThat(stats.weekParts().get(0).done()).isTrue();
    assertThat(stats.weekParts().get(1).name()).isEqualTo("背中");
    assertThat(stats.weekParts().get(1).done()).isTrue();
    assertThat(stats.weekParts().get(2).name()).isEqualTo("肩");
    assertThat(stats.weekParts().get(2).done()).isTrue();
    assertThat(stats.weekParts().get(3).name()).isEqualTo("腕");
    assertThat(stats.weekParts().get(3).done()).isFalse();
    assertThat(stats.weekParts().get(4).name()).isEqualTo("脚");
    assertThat(stats.weekParts().get(4).done()).isFalse();

    // Web用Map変換も同じ内容になっていること
    assertThat(stats.weekPartsAsMapList().get(0)).containsEntry("name", "胸").containsEntry("done", true);
    assertThat(stats.weekPartsAsMapList().get(3)).containsEntry("name", "腕").containsEntry("done", false);
  }

  @Test
  void 前週データが無い場合は前週データなしと表示しpositive扱いになる() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    LocalDate weekStart = LocalDate.of(2026, 9, 7);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(USER_ID, weekStart, TODAY))
        .thenReturn(500.0);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(
            USER_ID, weekStart.minusWeeks(1), weekStart.minusDays(1)))
        .thenReturn(null);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.volumeChangeText()).isEqualTo("前週データなし");
    assertThat(stats.volumeChangePositive()).isTrue();
  }

  @Test
  void 先週比が増加した場合はプラス表示になる() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    LocalDate weekStart = LocalDate.of(2026, 9, 7);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(USER_ID, weekStart, TODAY))
        .thenReturn(1200.0);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(
            USER_ID, weekStart.minusWeeks(1), weekStart.minusDays(1)))
        .thenReturn(1000.0);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.volumeChangeText()).isEqualTo("+20%");
    assertThat(stats.volumeChangePositive()).isTrue();
  }

  @Test
  void 先週比が減少した場合はマイナス表示になる() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    LocalDate weekStart = LocalDate.of(2026, 9, 7);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(USER_ID, weekStart, TODAY))
        .thenReturn(540.0);
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(
            USER_ID, weekStart.minusWeeks(1), weekStart.minusDays(1)))
        .thenReturn(1000.0);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.volumeChangeText()).isEqualTo("-46%");
    assertThat(stats.volumeChangePositive()).isFalse();
  }

  @Test
  void 今日の曜日別プログラムが設定されていれば部位ラベルを返す() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(null);
    WeeklyProgram program = new WeeklyProgram();
    program.setPartCode("LEG");
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.of(program));

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.todayProgram()).isSameAs(program);
    assertThat(stats.todayPartCode()).isEqualTo("LEG");
    assertThat(stats.todayPartLabel()).isEqualTo("脚");
  }

  @Test
  void 今日の曜日別プログラムが未設定なら部位ラベルはnull() {
    when(trainingDao.countByUserIdAndMonth(any(), anyInt(), anyInt())).thenReturn(0);
    when(trainingDao.selectDistinctPartsByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(List.of());
    when(trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(eq(USER_ID), any(), any()))
        .thenReturn(null);
    when(weeklyProgramService.getTodayProgram(USER_ID)).thenReturn(Optional.empty());

    TrainingStatsService.TrainingStats stats = service.getStats(USER_ID, TODAY);

    assertThat(stats.todayProgram()).isNull();
    assertThat(stats.todayPartCode()).isNull();
    assertThat(stats.todayPartLabel()).isNull();
  }
}
