package com.example.traning.organization;

/** スタッフが会員（ユーザー）に対してできることの段階（機能見直し-1-#15）。 */
public enum MemberAccessLevel {
  /** 見られない（一覧にも出さない）。 */
  NONE,
  /** トレーニング記録の閲覧のみ（トレーナーから見た、同じ店舗の担当外の会員）。 */
  RECORDS_ONLY,
  /** 記録・指導機能（アドバイス・期分けの提案など）。管理操作ができるかは役割で別に判定する。 */
  FULL;

  public boolean canViewRecords() {
    return this != NONE;
  }

  public boolean canCoach() {
    return this == FULL;
  }
}
