package com.example.traning.organization;

/** 組織階層の種別。 */
public enum OrganizationType {
  /** 全組織共通を表す予約済みの特殊行（id=0固定）。 */
  ALL,
  /** 組織（ジム、最上位）。 */
  GYM,
  /** 店舗。 */
  STORE
}
