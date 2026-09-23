package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.periodization.StagnationDetectionService.StagnationLevel;
import com.example.traning.training.dao.TrainingDetailDao.SessionAggregate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 機能見直し-1-#3 QA Q3-4: 停滞検知（A主軸＋B補助、判定窓3回）の判定ロジック。 */
class StagnationDetectionServiceTest {

  /** (maxWeight, totalVolume) の組を古い順に並べたセッション列を作る。 */
  private static List<SessionAggregate> sessions(double[][] rows) {
    List<SessionAggregate> list = new ArrayList<>();
    LocalDate d = LocalDate.of(2026, 9, 1);
    for (double[] r : rows) {
      SessionAggregate s = new SessionAggregate();
      s.trainingDate = d;
      s.maxWeight = r[0];
      s.totalVolume = r[1];
      list.add(s);
      d = d.plusDays(3);
    }
    return list;
  }

  @Test
  void 母数不足ならNONE() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 2000}, {80, 1900}, {80, 1800}})))
        .isEqualTo(StagnationLevel.NONE);
    assertThat(StagnationDetectionService.evaluateSessions(null)).isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void ボリュームが毎回伸びていればNONE() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 2000}, {80, 2100}, {80, 2200}, {80, 2300}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void ダブルプログレッションで重量据え置きでもレップ増加でボリュームが伸びていればNONE() {
    // 重量は同じ（Bは停滞）だがボリュームは増加（Aは非停滞）→ Aが主軸なので停滞としない
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 1920}, {80, 2000}, {80, 2080}, {80, 2160}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void 三回中二回ボリューム停滞かつ最大重量も二回連続更新なしならSTRONG() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 2000}, {82.5, 2100}, {82.5, 2050}, {80, 2000}})))
        .isEqualTo(StagnationLevel.STRONG);
  }

  @Test
  void ボリュームのみ停滞ならMILD() {
    // 最大重量は直近で更新しているがボリュームは3回中2回減少
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 2000}, {80, 1900}, {82.5, 2100}, {85, 1700}})))
        .isEqualTo(StagnationLevel.MILD);
  }

  @Test
  void 三回中一回だけのボリューム停滞は偶発ノイズとしてNONE() {
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(new double[][] {{80, 2000}, {80, 1800}, {80, 2100}, {80, 2200}})))
        .isEqualTo(StagnationLevel.NONE);
  }

  @Test
  void 窓より古いセッションは判定に使わない() {
    // 先頭2件は窓外。窓内（後ろ4件）は毎回増加
    assertThat(
            StagnationDetectionService.evaluateSessions(
                sessions(
                    new double[][] {
                      {100, 5000}, {100, 5000}, {80, 2000}, {80, 2100}, {80, 2200}, {80, 2300}
                    })))
        .isEqualTo(StagnationLevel.NONE);
  }
}
