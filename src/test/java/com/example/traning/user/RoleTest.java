package com.example.traning.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ita2-7（Role enum化）のヘルパーメソッド検証。 */
class RoleTest {

  @Test
  void valueはROLE_プレフィックス付きの文字列を返す() {
    assertThat(Role.USER.value()).isEqualTo("ROLE_USER");
    assertThat(Role.ADMIN.value()).isEqualTo("ROLE_ADMIN");
    assertThat(Role.ORG_ADMIN.value()).isEqualTo("ROLE_ORG_ADMIN");
    assertThat(Role.STORE_ADMIN.value()).isEqualTo("ROLE_STORE_ADMIN");
  }

  @Test
  void fromValueは正しい文字列を対応するenumに変換する() {
    assertThat(Role.fromValue("ROLE_USER")).isEqualTo(Role.USER);
    assertThat(Role.fromValue("ROLE_ADMIN")).isEqualTo(Role.ADMIN);
    assertThat(Role.fromValue("ROLE_ORG_ADMIN")).isEqualTo(Role.ORG_ADMIN);
    assertThat(Role.fromValue("ROLE_STORE_ADMIN")).isEqualTo(Role.STORE_ADMIN);
  }

  @Test
  void fromValueはnullの場合nullを返す() {
    assertThat(Role.fromValue(null)).isNull();
  }

  @Test
  void fromValueは不正な値の場合nullを返す() {
    assertThat(Role.fromValue("INVALID")).isNull();
    assertThat(Role.fromValue("USER")).isNull(); // ROLE_プレフィックス無しは不正
    assertThat(Role.fromValue("")).isNull();
  }

  @Test
  void valueとfromValueは相互変換できる() {
    for (Role role : Role.values()) {
      assertThat(Role.fromValue(role.value())).isEqualTo(role);
    }
  }
}
