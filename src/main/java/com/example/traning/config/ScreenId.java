package com.example.traning.config;

import java.util.Arrays;
import java.util.Optional;

/**
 * 画面ID→タイトル・ヘッダーの戻り先URL・戻り先ラベルの静的マップ（ita2-4）。
 *
 * <p>{@code common.js}のリクエストパス文字列分岐（{@code window.location.pathname}の場当たり的な比較）を廃止し、
 * 画面ごとの戻り先を1箇所で管理するために導入した。{@link #backUrl()}が{@code null}の画面（{@link
 * #START_TRAINING}のみ）は、遷移元が複数あり固定の戻り先を1つに決められないため、ブラウザの{@code history.back()}を維持する。
 */
public enum ScreenId {
  PR("/pr", "自己記録（PR）", "/menu", "メニューに戻る"),
  TRAINING_DETAIL("/detail", "トレーニング詳細", "/menu", "メニューに戻る"),
  TRAINING_TEMPLATE("/training/template", "テンプレート管理", "/menu", "メニューに戻る"),
  TRAINING_REGISTER("/training/register", "種目を登録", "/menu", "メニューに戻る"),
  START_TRAINING("/start/training", "トレーニング実施", null, null),
  USER_PROFILE("/user/profile", "プロフィール", "/menu", "メニューに戻る"),
  USER_NOTIFICATIONS("/user/notifications", "通知設定", "/user/profile", "プロフィールに戻る"),
  USER_GOALS("/user/goals", "目標設定", "/menu", "メニューに戻る"),
  USER_BODY("/user/body", "体重記録", "/menu", "メニューに戻る"),
  USER_EXPORT("/user/export", "データエクスポート", "/menu", "メニューに戻る"),
  USER_EMAIL("/user/email", "メールアドレス変更", "/menu", "メニューに戻る"),
  USER_WEEKLY_PROGRAM("/user/weekly-program", "週間プログラム", "/menu", "メニューに戻る"),
  USER_WITHDRAWAL("/user/withdrawal", "退会申請", "/menu", "メニューに戻る"),
  USER_MFA("/user/mfa", "2段階認証", "/menu", "メニューに戻る"),
  USER_MFA_SETUP("/user/mfa/setup", "2段階認証設定", "/user/mfa", "2段階認証に戻る"),
  USER_MFA_BACKUP_CODES("/user/mfa/backup-codes", "バックアップコード", "/user/mfa", "2段階認証に戻る"),
  NOTICE_LIST("/notices", "お知らせ一覧", "/menu", "メニューに戻る"),
  NOTICE_MANAGE("/notices/manage", "お知らせ管理", "/menu", "メニューに戻る"),
  ADMIN_USERS("/admin/users", "ユーザー管理", "/menu", "メニューに戻る"),
  ADMIN_USER_EDIT("/admin/user/edit/", "ユーザー編集", "/admin/users", "一覧に戻る"),
  ADMIN_AUDIT_LOGS("/admin/audit-logs", "監査ログ", "/admin/users", "一覧に戻る"),
  ADMIN_DELETED_USERS("/admin/deleted/users", "削除済みユーザー", "/admin/users", "一覧に戻る"),
  ADMIN_DELETED_TRAININGS(
      "/admin/deleted/trainings", "削除済みトレーニング", "/admin/deleted/users", "一覧に戻る"),
  ADMIN_WITHDRAWAL("/admin/withdrawal", "退会申請一覧", "/menu", "メニューに戻る"),
  ADMIN_MASTER("/admin/master", "マスタ管理", "/menu", "メニューに戻る"),
  ADMIN_ORGANIZATIONS("/admin/organizations", "組織・店舗管理", "/menu", "メニューに戻る"),
  ADMIN_INVITE_CODES("/admin/invite-codes", "招待コード管理", "/menu", "メニューに戻る"),
  ADMIN_ALL_USERS_TRAINING("/admin/all-users-training", "全ユーザートレーニング一覧", "/menu", "メニューに戻る"),
  ADMIN_USER_TRAINING_DETAIL(
      "/admin/user/training-detail/", "トレーニング詳細", "/admin/all-users-training", "一覧に戻る"),
  TRAINER_ADVICE("/trainer/advice", "トレーナーアドバイス", "/menu", "メニューに戻る"),
  ;

  private final String path;
  private final String title;
  private final String backUrl;
  private final String backLabel;

  ScreenId(String path, String title, String backUrl, String backLabel) {
    this.path = path;
    this.title = title;
    this.backUrl = backUrl;
    this.backLabel = backLabel;
  }

  public String title() {
    return title;
  }

  public String backUrl() {
    return backUrl;
  }

  public String backLabel() {
    return backLabel;
  }

  /**
   * リクエストパスから対応する画面を判定する。{@code /admin/user/edit/{id}}
   * のような動的セグメントを含む画面は登録パスを前方一致で、それ以外は完全一致で判定する（登録パスが{@code /}で終わるものが前方一致対象）。
   */
  public static Optional<ScreenId> fromPath(String requestUri) {
    if (requestUri == null) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(
            s -> s.path.endsWith("/") ? requestUri.startsWith(s.path) : requestUri.equals(s.path))
        .findFirst();
  }
}
