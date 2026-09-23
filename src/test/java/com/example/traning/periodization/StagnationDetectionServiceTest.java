package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.periodization.StagnationDetectionService.Session;
import com.example.traning.periodization.StagnationDetectionService.StagnationLevel;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dao.TrainingDetailDao.SessionAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 機能見直し-1-#3 QA Q3-4（2026-09-24確定）: 停滞判定の比較基準は「判定窓（直近3回）の直前のセッション」。 ディロード週・低強度週は除外してさかのぼる。 */
@ExtendWith(MockitoExtension.class)
class StagnationDetectionServiceTest {

  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private ProgramCycleDao programCycleDao;
  @Mock private ItemStagnationEvaluationDao itemStagnationEvaluationDao;

  private static final LocalDate D = LocalDate.of(2026, 9, 30);

  /** 新しい順。各行は {最大重量, 合計ボリューム}。期分けプログラム外のセッション。 */
  private static List<Session> plain(double[][] rows) {
    List<Session> list = new ArrayList<>();
    for (int i = 0; i < rows.length; i++) {
      list.add(new Session(D.minusDays(3L * i), rows[i][1], rows[i][0], false, null));
    }
    return list;
  }

  private static Session s(double maxWeight, double volume, boolean deload, String pct) {
    return new Session(D, volume, maxWeight, deload, pct == null ? null : new BigDecimal(pct));
  }

  @Test
  void 記録が4回未満なら判定不可() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1800}, {80, 1900}, {80, 2000}})))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
    assertThat(StagnationDetectionService.evaluateSessions(null))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
  }

  @Test
  void 窓内のどれかが基準を上回っていれば停滞ではない() {
    // 基準(最古)=2000。窓の中で2100が上回っている
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1900}, {80, 2100}, {80, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void 窓内のどれも基準の合計ボリュームを上回らなければMILD() {
    // 最大重量は窓内で基準80を上回る（Bは非停滞）
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{82.5, 1900}, {80, 2000}, {80, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.MILD);
  }

  @Test
  void 合計ボリュームも最大重量も基準を上回らなければSTRONG() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1900}, {80, 2000}, {77.5, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void 最大重量のみ停滞ならNONE() {
    // ダブルプログレッション: 重量据え置きでレップが伸び、ボリュームが基準を上回る
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 2160}, {80, 2080}, {80, 2000}, {80, 1920}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void ディロード週のセッションは窓と基準の両方から除外してさかのぼる() {
    List<Session> sessions =
        List.of(
            s(80, 1900, false, "80.0"),
            s(60, 900, true, "60.0"), // ディロード週: 除外
            s(80, 1950, false, "80.0"),
            s(80, 2000, false, "80.0"),
            s(80, 2000, false, "75.0")); // 基準
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void ディロード週を除くと4回に満たなければ判定不可() {
    List<Session> sessions =
        List.of(
            s(80, 1900, false, "80.0"),
            s(60, 900, true, "60.0"),
            s(80, 1950, false, "80.0"),
            s(80, 2000, false, "80.0"));
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
  }

  @Test
  void 計画強度が基準より低い週のセッションは除外してさかのぼる() {
    // 窓の2件目は計画強度65%で基準(75%)より低い → 除外し、さらに過去を補う
    List<Session> sessions =
        List.of(
            s(80, 1900, false, "80.0"),
            s(70, 2600, false, "65.0"), // 低強度週: 除外（除外しなければ基準を上回りNONEになる）
            s(80, 1950, false, "80.0"),
            s(80, 2000, false, "75.0"),
            s(80, 2000, false, "75.0"));
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void 期分けプログラム外のセッションは計画強度で除外しない() {
    List<Session> sessions =
        List.of(
            s(80, 2600, false, null), // プログラム外: 通常どおり対象（基準を上回る）
            s(80, 1950, false, "80.0"),
            s(80, 1900, false, "80.0"),
            s(80, 2000, false, "80.0"));
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void evaluateAndStore_判定結果を保持する() {
    StagnationDetectionService service =
        new StagnationDetectionService(
            trainingDetailDao, programCycleDao, itemStagnationEvaluationDao);
    List<SessionAggregate> rows = new ArrayList<>();
    double[][] data = {{80, 1900}, {80, 2000}, {77.5, 1950}, {80, 2000}};
    for (int i = 0; i < data.length; i++) {
      SessionAggregate a = new SessionAggregate();
      a.trainingDate = D.minusDays(3L * i);
      a.maxWeight = data[i][0];
      a.totalVolume = data[i][1];
      rows.add(a);
    }
    when(trainingDetailDao.selectRecentSessionAggregatesByItem(eq(1L), eq("ベンチプレス"), anyInt()))
        .thenReturn(rows);
    when(programCycleDao.selectOverlappingByUserId(anyLong(), any(), any())).thenReturn(List.of());

    service.evaluateAndStore(1L, "ベンチプレス");

    verify(itemStagnationEvaluationDao).upsert(eq(1L), eq("ベンチプレス"), eq("STRONG"), any());
  }

  @Test
  void evaluateAndStore_判定中の例外は保存処理に伝えない() {
    StagnationDetectionService service =
        new StagnationDetectionService(
            trainingDetailDao, programCycleDao, itemStagnationEvaluationDao);
    when(trainingDetailDao.selectRecentSessionAggregatesByItem(anyLong(), anyString(), anyInt()))
        .thenThrow(new RuntimeException("db down"));
    service.evaluateAndStore(1L, "ベンチプレス"); // 例外が出なければOK
  }

  private static int anyInt() {
    return org.mockito.ArgumentMatchers.anyInt();
  }
}
