package com.example.traning.training;

/**
 * セット種別。DB列（{@code training_details.set_type} 等）は文字列のまま保持し（{@link
 * com.example.traning.organization.OrganizationType} と同じ方針）、判定・表示ロジックはこのenum経由で行う。
 */
public enum SetType {
  MAIN("メイン"),
  WARMUP("ウォームアップ"),
  DROP("ドロップ");

  private final String label;

  SetType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** ボリューム集計・PR計算から除外すべきセット種別かどうか。 */
  public boolean isVolumeExcluded() {
    return this == WARMUP || this == DROP;
  }

  /** 不正な値・未指定の場合は MAIN を返す。 */
  public static SetType fromValueOrMain(String value) {
    if (value == null) {
      return MAIN;
    }
    for (SetType type : values()) {
      if (type.name().equals(value)) {
        return type;
      }
    }
    return MAIN;
  }

  public static boolean isValid(String value) {
    if (value == null) {
      return false;
    }
    for (SetType type : values()) {
      if (type.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
