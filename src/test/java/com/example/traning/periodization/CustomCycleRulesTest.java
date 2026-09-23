package com.example.traning.periodization;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.periodization.CustomCycleRules.Warning;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 白紙作成の補足・注意表示ルール（2026-09-23 USER承認）。いずれも保存は妨げない。 */
class CustomCycleRulesTest {

  /** 週数と、ディロード週にする週番号（1始まり）からフラグ列を作る。 */
  private static List<Boolean> weeks(int total, int... deloadWeeks) {
    List<Boolean> list = new ArrayList<>();
    for (int i = 1; i <= total; i++) {
      boolean d = false;
      for (int w : deloadWeeks) d |= (w == i);
      list.add(d);
    }
    return list;
  }

  @Test
  void 二週サイクルは補足を出す() {
    assertThat(CustomCycleRules.evaluate(weeks(2, 2))).containsExactly(Warning.SHORT_CYCLE);
  }

  @Test
  void 四週以下でディロード無しは注意なし() {
    assertThat(CustomCycleRules.evaluate(weeks(4))).isEmpty();
  }

  @Test
  void 五週以上でディロードが1つも無ければ注意() {
    assertThat(CustomCycleRules.evaluate(weeks(5))).containsExactly(Warning.NO_DELOAD);
  }

  @Test
  void 負荷週が七週連続なら注意() {
    // 8週目だけディロード → 1〜7週目が負荷週7連続
    assertThat(CustomCycleRules.evaluate(weeks(8, 8))).containsExactly(Warning.LONG_LOAD_STREAK);
  }

  @Test
  void 負荷週が六週連続までなら連続の注意は出さない() {
    assertThat(CustomCycleRules.evaluate(weeks(12, 7, 12))).isEmpty();
  }

  @Test
  void 七週以上ディロード無しなら両方の注意() {
    assertThat(CustomCycleRules.evaluate(weeks(7)))
        .containsExactly(Warning.LONG_LOAD_STREAK, Warning.NO_DELOAD);
  }
}
