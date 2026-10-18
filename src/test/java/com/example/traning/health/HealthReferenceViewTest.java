package com.example.traning.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 機能見直し-1-#5: Web版「体調の参考情報」カードの表示文言（モバイルのreferenceFormat.tsと揃える）。 */
class HealthReferenceViewTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 9);

  @Test
  void 睡眠は時間と分で表示し平均との差を長い短い同じで表す() {
    var s = new HealthReferenceView.Sleep(DATE, 390, "MANUAL", 432, 18);
    assertThat(s.dateLabel()).isEqualTo("10/9");
    assertThat(s.durationLabel()).isEqualTo("6時間30分");
    assertThat(s.averageLabel()).isEqualTo("4週間平均 7時間12分");
    assertThat(s.diffLabel()).isEqualTo("平均より42分短い");
    assertThat(new HealthReferenceView.Sleep(DATE, 500, "MANUAL", 432, 3).diffLabel())
        .isEqualTo("平均より1時間8分長い");
    assertThat(new HealthReferenceView.Sleep(DATE, 432, "MANUAL", 432, 3).diffLabel())
        .isEqualTo("平均と同じ");
  }

  @Test
  void 徹夜の0分は0時間0分と表示する() {
    assertThat(new HealthReferenceView.Sleep(DATE, 0, "MANUAL", null, 0).durationLabel())
        .isEqualTo("0時間0分");
  }

  @Test
  void 平均が無いときは確定文言を出し差は出さない() {
    var s = new HealthReferenceView.Sleep(DATE, 390, "MANUAL", null, 0);
    assertThat(s.averageLabel()).isEqualTo("直近4週間の記録がまだないため、平均は表示できません");
    assertThat(s.diffLabel()).isNull();
    var r = new HealthReferenceView.RestingHeartRate(DATE, 58, "HEALTHKIT", null, 0);
    assertThat(r.averageLabel()).isEqualTo("直近4週間の記録がまだないため、平均は表示できません");
    assertThat(r.diffLabel()).isNull();
  }

  @Test
  void 安静時心拍の平均との差を高い低い同じで表す() {
    assertThat(new HealthReferenceView.RestingHeartRate(DATE, 58, "HEALTHKIT", 55, 20).diffLabel())
        .isEqualTo("平均より3 bpm高い");
    assertThat(new HealthReferenceView.RestingHeartRate(DATE, 52, "HEALTHKIT", 55, 20).diffLabel())
        .isEqualTo("平均より3 bpm低い");
    assertThat(new HealthReferenceView.RestingHeartRate(DATE, 55, "HEALTHKIT", 55, 20).diffLabel())
        .isEqualTo("平均と同じ");
    assertThat(new HealthReferenceView.RestingHeartRate(DATE, 55, "HEALTHKIT", 55, 20).averageLabel())
        .isEqualTo("4週間平均 55 bpm");
  }
}
