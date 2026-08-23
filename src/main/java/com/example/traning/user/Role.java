package com.example.traning.user;

/**
 * ユーザー権限（ロール）。DB列（{@code users.role}）・Spring Securityの{@code GrantedAuthority}は 引き続き{@code "ROLE_"
 * + name()}形式の文字列のまま扱う（{@link com.example.traning.organization.OrganizationType}
 * と同じ方針）。判定ロジックはこのenum経由で行う。
 */
public enum Role {
  USER,
  ADMIN,
  ORG_ADMIN,
  STORE_ADMIN,
  TRAINER;

  /** DB列・Spring Securityの{@code GrantedAuthority}に格納する形式（例: {@code "ROLE_ADMIN"}）。 */
  public String value() {
    return "ROLE_" + name();
  }

  /** 該当するロールが存在しない・不正な値の場合は {@code null} を返す。 */
  public static Role fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (Role role : values()) {
      if (role.value().equals(value)) {
        return role;
      }
    }
    return null;
  }
}
