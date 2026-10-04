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

/** 機能見直し-1-#3 QA Q3-4（2026-09-24 比較方法の訂正）: 判定窓の各セッションを、それより古い同じ計画強度の直近の回と比べる。 */
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
  void 記録が判定窓3件と比較相手に満たなければ判定不可() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1800}, {80, 1900}, {80, 2000}})))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
    assertThat(StagnationDetectionService.evaluateSessions(null))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
  }

  @Test
  void 計画外のセッションは判定窓の直前の計画外セッションと比べる_どれかが上回ればNONE() {
    // 比較相手=4件目(2000)。窓の2100が上回る
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1900}, {80, 2100}, {80, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void 窓の3件とも比較相手の合計ボリュームを上回らなければMILD() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{82.5, 1900}, {80, 2000}, {80, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.MILD);
  }

  @Test
  void 合計ボリュームも最大重量も上回らなければSTRONG() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 1900}, {80, 2000}, {77.5, 1950}, {80, 2000}})))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void 最大重量のみ停滞ならNONE() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                plain(new double[][] {{80, 2160}, {80, 2080}, {80, 2000}, {80, 1920}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  /** 波状の強度（70→75→80(ピーク)→ディロード→70→75→80）。新しい順に並べる。 */
  private static List<Session> wave(double peakVolumeLatest) {
    return List.of(
        s(90, peakVolumeLatest, false, "80.0"), // 最新: 2サイクル目のピーク週
        s(85, 2300, false, "75.0"),
        s(80, 2500, false, "70.0"),
        s(60, 900, true, "60.0"), // ディロード週: 除外
        s(90, 1900, false, "80.0"), // 1サイクル目のピーク週
        s(85, 2300, false, "75.0"),
        s(80, 2500, false, "70.0"));
  }

  @Test
  void 波状の強度でも最新のピーク週セッションが判定窓に入り同じ強度の回と比べられる() {
    // 最新(80%)は前サイクルの80%(1900)と比べる。強度が低いという理由で窓から外さないので、
    // 最新のピーク週の伸び(2000>1900)が判定に使われNONEになる
    assertThat(StagnationDetectionService.evaluateSessions(wave(2000)))
        .isEqualTo(StagnationLevel.NONE);
    // 最新のピーク週も前サイクルを上回らなければ（1850<=1900）、75%・70%の回も前サイクル以下なので停滞
    assertThat(StagnationDetectionService.evaluateSessions(wave(1850)))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void 判定窓に同じ強度の比較相手が無い回があれば判定不可() {
    // 最初の1サイクル: 80%・75%・70%とも、より古い同じ強度の回が無い
    List<Session> sessions =
        List.of(
            s(90, 1900, false, "80.0"),
            s(85, 2300, false, "75.0"),
            s(80, 2500, false, "70.0"),
            s(80, 2400, false, null));
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.INSUFFICIENT);
  }

  @Test
  void ディロード週のセッションは判定窓にも比較相手にも使わない() {
    List<Session> sessions =
        List.of(
            s(80, 1900, false, "80.0"),
            s(60, 3000, true, "80.0"), // ディロード週（強度が同じでも比較相手にしない）
            s(80, 1950, false, "80.0"),
            s(80, 2000, false, "80.0"),
            s(80, 2000, false, "80.0"));
    // 窓=1900,1950,2000(80%) → 比較相手=それぞれ直後の80%の回(1950,2000,2000)
    assertThat(StagnationDetectionService.evaluateSessions(sessions))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void 強度の一致は完全一致で判定する() {
    assertThat(
            StagnationDetectionService.sameIntensity(new BigDecimal("80.0"), new BigDecimal("80")))
        .isTrue();
    assertThat(
            StagnationDetectionService.sameIntensity(
                new BigDecimal("80.0"), new BigDecimal("77.5")))
        .isFalse();
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
