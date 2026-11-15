package com.example.traning.organization;

/** 招待コードの用途（機能見直し-1-#15・itバグ-44）。店舗管理者はコードでは作らず、ジム管理者が役割を変えて任命する。 */
public enum InviteCodePurpose {
  /** 会員の新規登録（/signup）用。 */
  MEMBER("会員用"),
  /** トレーナー登録（/signup/trainer）用。発行できるのはジム管理者とシステム管理者のみ。 */
  TRAINER("トレーナー用");

  private final String label;

  InviteCodePurpose(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** 該当する用途が無い・不正な値の場合は {@code null} を返す。 */
  public static InviteCodePurpose fromName(String name) {
    if (name == null) {
      return null;
    }
    for (InviteCodePurpose purpose : values()) {
      if (purpose.name().equals(name)) {
        return purpose;
      }
    }
    return null;
  }
}
