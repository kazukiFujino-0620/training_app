package com.example.traning.health;

/**
 * 睡眠・安静時心拍の手動入力を保存しなかったことを表す（機能見直し-1-#5）。
 *
 * <p>{@link #isConfirmationRequired()}がtrueのときは確認範囲の値で、利用者の確認（confirmed=true）があれば保存できる。
 * falseのときは入力エラー範囲の値で、保存できない。
 */
public class HealthInputRejectedException extends RuntimeException {

  /** クライアントが確認表示を出すために見るコード。 */
  public static final String CODE_CONFIRMATION_REQUIRED = "CONFIRMATION_REQUIRED";

  private final boolean confirmationRequired;

  private HealthInputRejectedException(String message, boolean confirmationRequired) {
    super(message);
    this.confirmationRequired = confirmationRequired;
  }

  public static HealthInputRejectedException error(String message) {
    return new HealthInputRejectedException(message, false);
  }

  public static HealthInputRejectedException confirmationRequired() {
    return new HealthInputRejectedException(HealthInputLimits.CONFIRM_MESSAGE, true);
  }

  public boolean isConfirmationRequired() {
    return confirmationRequired;
  }

  /** 判定結果に応じて例外を投げる。OKなら何もしない。 */
  static void throwIfRejected(
      HealthInputLimits.Check check, boolean confirmed, String errorMessage) {
    switch (check) {
      case ERROR -> throw error(errorMessage);
      case NEEDS_CONFIRMATION -> {
        if (!confirmed) {
          throw confirmationRequired();
        }
      }
      case OK -> {}
    }
  }
}
