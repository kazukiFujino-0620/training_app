package com.example.traning.training;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ita2-6（setType enum化）のヘルパーメソッド検証。 */
class SetTypeTest {

  @Test
  void fromValueOrMainは正しい値の文字列を対応するenumに変換する() {
    assertThat(SetType.fromValueOrMain("MAIN")).isEqualTo(SetType.MAIN);
    assertThat(SetType.fromValueOrMain("WARMUP")).isEqualTo(SetType.WARMUP);
    assertThat(SetType.fromValueOrMain("DROP")).isEqualTo(SetType.DROP);
  }

  @Test
  void fromValueOrMainはnullの場合MAINを返す() {
    assertThat(SetType.fromValueOrMain(null)).isEqualTo(SetType.MAIN);
  }

  @Test
  void fromValueOrMainは不正な値の場合MAINを返す() {
    assertThat(SetType.fromValueOrMain("INVALID")).isEqualTo(SetType.MAIN);
    assertThat(SetType.fromValueOrMain("")).isEqualTo(SetType.MAIN);
    assertThat(SetType.fromValueOrMain("main")).isEqualTo(SetType.MAIN); // 大文字小文字を区別する
  }

  @Test
  void isValidは正しい値のみtrueを返す() {
    assertThat(SetType.isValid("MAIN")).isTrue();
    assertThat(SetType.isValid("WARMUP")).isTrue();
    assertThat(SetType.isValid("DROP")).isTrue();
    assertThat(SetType.isValid("INVALID")).isFalse();
    assertThat(SetType.isValid(null)).isFalse();
  }

  @Test
  void isVolumeExcludedはWARMUPとDROPのみtrueを返す() {
    assertThat(SetType.MAIN.isVolumeExcluded()).isFalse();
    assertThat(SetType.WARMUP.isVolumeExcluded()).isTrue();
    assertThat(SetType.DROP.isVolumeExcluded()).isTrue();
  }

  @Test
  void getLabelは日本語ラベルを返す() {
    assertThat(SetType.MAIN.getLabel()).isEqualTo("メイン");
    assertThat(SetType.WARMUP.getLabel()).isEqualTo("ウォームアップ");
    assertThat(SetType.DROP.getLabel()).isEqualTo("ドロップ");
  }
}
