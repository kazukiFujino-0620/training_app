package com.example.traning.smarttrainer.autogen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.traning.smarttrainer.autogen.StrengthEstimator.SessionSet;
import com.example.traning.smarttrainer.autogen.StrengthEstimator.Strength;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 機能見直し-1-#4 5-4節（QA Q4-12）: 直近の記録からの推定1RM。 */
class StrengthEstimatorTest {

  private static final LocalDate DATE = LocalDate.of(2026, 11, 11);
  private final OneRmPredictionService epley = new OneRmPredictionService();

  private static SessionSet s(int daysAgo, double weight, int reps) {
    return new SessionSet(DATE.minusDays(daysAgo), weight, reps);
  }

  private Strength estimate(SessionSet... sets) {
    return StrengthEstimator.estimate(List.of(sets), DATE, epley);
  }

  @Test
  void 直近3セッションそれぞれの推定1RM最大値の平均() {
    // 各セッションの最大: 140（120×5）、107.67（85×8）、106.67（80×10）
    Strength st = estimate(s(0, 80, 10), s(0, 80, 10), s(5, 85, 8), s(5, 60, 12), s(14, 120, 5));
    assertThat(st.oneRm())
        .isCloseTo((140 + 85 * (1 + 8 / 30.0) + 80 * (1 + 10 / 30.0)) / 3, within(1e-9));
  }

  @Test
  void セッションが4回以上あっても直近3セッションだけ使う() {
    Strength st = estimate(s(1, 100, 1), s(2, 100, 1), s(3, 100, 1), s(4, 200, 1));
    assertThat(st.oneRm()).isEqualTo(100.0);
  }

  @Test
  void セッションが2回ならその2回で平均_1回ならその値() {
    assertThat(estimate(s(1, 100, 1), s(8, 80, 1)).oneRm()).isEqualTo(90.0);
    assertThat(estimate(s(1, 100, 1)).oneRm()).isEqualTo(100.0);
  }

  @Test
  void 回数13回以上のセットは推定1RMに使わない() {
    // 50×20（推定83.3）は除外し、60×10（推定80）を使う
    assertThat(estimate(s(1, 50, 20), s(1, 60, 10)).oneRm()).isCloseTo(80.0, within(1e-9));
    // 12回はちょうど使う
    assertThat(estimate(s(1, 60, 12)).oneRm()).isCloseTo(84.0, within(1e-9));
  }

  @Test
  void 回数12回以下のセットが無いセッションは平均に含めない() {
    assertThat(estimate(s(1, 50, 15), s(5, 60, 10)).oneRm()).isCloseTo(80.0, within(1e-9));
  }

  @Test
  void 回数12回以下の記録が無ければ推定1RMは無く_直近の重量を使う() {
    Strength st = estimate(s(1, 45, 15), s(1, 50, 13), s(9, 40, 20));
    assertThat(st.oneRm()).isNull();
    assertThat(st.lastMainWeight()).isEqualTo(50.0);
  }

  @Test
  void 直近セッションの最大作業重量とその回数_最後にやってからの日数() {
    Strength st = estimate(s(3, 80, 10), s(3, 82.5, 6), s(3, 82.5, 8), s(10, 100, 3));
    assertThat(st.lastSessionMaxWeight()).isEqualTo(82.5);
    assertThat(st.lastSessionReps()).isEqualTo(8);
    assertThat(st.daysSinceLast()).isEqualTo(3);
  }

  @Test
  void 対象日より後の記録は使わない_記録が無ければ何も無い() {
    Strength st = estimate(new SessionSet(DATE.plusDays(1), 200, 1), s(2, 100, 1));
    assertThat(st.oneRm()).isEqualTo(100.0);
    assertThat(StrengthEstimator.estimate(List.of(), DATE, epley)).isEqualTo(Strength.NONE);
  }
}
