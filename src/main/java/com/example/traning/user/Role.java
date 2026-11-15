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
  /**
   * トレーナー（機能見直し-1-#15）。担当会員（{@code users.assigned_trainer_id}が自分）には指導機能、
   * 同じ店舗の担当外の会員にはトレーニング記録の閲覧のみ。管理操作は不可。
   */
  TRAINER;

  /** 会員の担当トレーナーになれる役割（担当の割り当て先・アドバイスの送信者）。 */
  public boolean canBeAssignedTrainer() {
    return this == TRAINER || this == STORE_ADMIN || this == ORG_ADMIN;
  }

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
