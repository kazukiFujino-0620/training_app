package com.example.traning.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** ita2-4（ヘッダー戻る/閉じる対応）: {@link ScreenId#fromPath}が全26画面を正しく解決できるかの検証。 */
class ScreenIdTest {

  static Stream<Arguments> exactMatchScreens() {
    return Stream.of(
        arguments("/pr", ScreenId.PR, "/menu", "メニューに戻る"),
        arguments("/detail", ScreenId.TRAINING_DETAIL, "/menu", "メニューに戻る"),
        arguments("/training/template", ScreenId.TRAINING_TEMPLATE, "/menu", "メニューに戻る"),
        arguments("/training/register", ScreenId.TRAINING_REGISTER, "/menu", "メニューに戻る"),
        arguments("/user/profile", ScreenId.USER_PROFILE, "/menu", "メニューに戻る"),
        arguments("/user/goals", ScreenId.USER_GOALS, "/menu", "メニューに戻る"),
        arguments("/user/body", ScreenId.USER_BODY, "/menu", "メニューに戻る"),
        arguments("/user/export", ScreenId.USER_EXPORT, "/menu", "メニューに戻る"),
        arguments("/user/email", ScreenId.USER_EMAIL, "/menu", "メニューに戻る"),
        arguments("/user/weekly-program", ScreenId.USER_WEEKLY_PROGRAM, "/menu", "メニューに戻る"),
        arguments("/user/withdrawal", ScreenId.USER_WITHDRAWAL, "/menu", "メニューに戻る"),
        arguments("/user/mfa", ScreenId.USER_MFA, "/menu", "メニューに戻る"),
        arguments("/user/mfa/setup", ScreenId.USER_MFA_SETUP, "/user/mfa", "2段階認証に戻る"),
        arguments(
            "/user/mfa/backup-codes", ScreenId.USER_MFA_BACKUP_CODES, "/user/mfa", "2段階認証に戻る"),
        arguments("/notices", ScreenId.NOTICE_LIST, "/menu", "メニューに戻る"),
        arguments("/notices/manage", ScreenId.NOTICE_MANAGE, "/menu", "メニューに戻る"),
        arguments("/admin/users", ScreenId.ADMIN_USERS, "/menu", "メニューに戻る"),
        arguments("/admin/audit-logs", ScreenId.ADMIN_AUDIT_LOGS, "/admin/users", "一覧に戻る"),
        arguments("/admin/deleted/users", ScreenId.ADMIN_DELETED_USERS, "/admin/users", "一覧に戻る"),
        arguments(
            "/admin/deleted/trainings",
            ScreenId.ADMIN_DELETED_TRAININGS,
            "/admin/deleted/users",
            "一覧に戻る"),
        arguments("/admin/withdrawal", ScreenId.ADMIN_WITHDRAWAL, "/menu", "メニューに戻る"),
        arguments("/admin/master", ScreenId.ADMIN_MASTER, "/menu", "メニューに戻る"),
        arguments(
            "/admin/all-users-training", ScreenId.ADMIN_ALL_USERS_TRAINING, "/menu", "メニューに戻る"));
  }

  @ParameterizedTest(name = "{0} は {1} に解決され、戻り先={2}({3})")
  @MethodSource("exactMatchScreens")
  void 完全一致する画面が正しく解決される(
      String path, ScreenId expected, String expectedBackUrl, String expectedBackLabel) {
    Optional<ScreenId> resolved = ScreenId.fromPath(path);

    assertThat(resolved).contains(expected);
    assertThat(resolved.get().backUrl()).isEqualTo(expectedBackUrl);
    assertThat(resolved.get().backLabel()).isEqualTo(expectedBackLabel);
  }

  @Test
  void 動的セグメントを含むユーザー編集画面が前方一致で解決される() {
    Optional<ScreenId> resolved = ScreenId.fromPath("/admin/user/edit/123");

    assertThat(resolved).contains(ScreenId.ADMIN_USER_EDIT);
    assertThat(resolved.get().backUrl()).isEqualTo("/admin/users");
    assertThat(resolved.get().backLabel()).isEqualTo("一覧に戻る");
  }

  @Test
  void 動的セグメントを含む管理者向けトレーニング詳細画面が前方一致で解決される() {
    Optional<ScreenId> resolved = ScreenId.fromPath("/admin/user/training-detail/456");

    assertThat(resolved).contains(ScreenId.ADMIN_USER_TRAINING_DETAIL);
    assertThat(resolved.get().backUrl()).isEqualTo("/admin/all-users-training");
    assertThat(resolved.get().backLabel()).isEqualTo("一覧に戻る");
  }

  @Test
  void トレーニング実施画面は遷移元が複数あるため戻り先URLがnullでhistory_backを維持する() {
    Optional<ScreenId> resolved = ScreenId.fromPath("/start/training");

    assertThat(resolved).contains(ScreenId.START_TRAINING);
    assertThat(resolved.get().backUrl()).isNull();
    assertThat(resolved.get().backLabel()).isNull();
  }

  @Test
  void 未登録の画面パスは空を返す() {
    assertThat(ScreenId.fromPath("/menu")).isEmpty(); // メニュー画面自体はScreenId対象外（ログアウト特別扱い）
    assertThat(ScreenId.fromPath("/unknown/path")).isEmpty();
  }

  @Test
  void nullパスは空を返す() {
    assertThat(ScreenId.fromPath(null)).isEmpty();
  }

  @Test
  void 前方一致対象の画面はプレフィックスが完全一致する別画面と誤認しない() {
    // /admin/user/edit/ は前方一致だが、/admin/users（完全一致画面）とは別画面として解決される
    assertThat(ScreenId.fromPath("/admin/users")).contains(ScreenId.ADMIN_USERS);
    assertThat(ScreenId.fromPath("/admin/users/999")).isEmpty(); // 想定外パスなので空
  }
}
