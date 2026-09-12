package com.example.traning.common;

/** Web画面（Thymeleaf）向けのエラー識別子。ログ相関・QA問い合わせ照合に使う。 */
public final class WebErrorCode {

  private WebErrorCode() {}

  /** メールアドレス重複（新規登録・メールアドレス変更共通） */
  public static final String EMAIL_DUPLICATE = "EMAIL_DUPLICATE";

  /** 論理削除済みアカウントの復元が必要（現状はリダイレクトのみでerrorMessage未使用） */
  public static final String ACCOUNT_RESTORE_REQUIRED = "ACCOUNT_RESTORE_REQUIRED";

  /** 入力値のバリデーション・業務ルール違反（フォーム入力起因） */
  public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

  /** トークン（パスワードリセット・アカウント復元・メールアドレス変更）が無効または期限切れ */
  public static final String TOKEN_INVALID_OR_EXPIRED = "TOKEN_INVALID_OR_EXPIRED";

  /** 対象の状態が操作の前提を満たしていない（二重申請・処理済み・権限外ロール変更など） */
  public static final String INVALID_STATE = "INVALID_STATE";

  /** 業務処理そのものが失敗した（バッチ実行・承認処理など、入力値の問題ではないケース） */
  public static final String OPERATION_FAILED = "OPERATION_FAILED";

  /** 想定外のシステムエラー（ERROR + スタックトレースの対象） */
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
