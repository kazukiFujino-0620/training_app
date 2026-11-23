package com.example.traning.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.audit.AuditLogController;
import com.example.traning.follow.MemberFollowController;
import com.example.traning.notice.NoticeController;
import com.example.traning.organization.AdminGymSettingController;
import com.example.traning.organization.AdminInviteCodeController;
import com.example.traning.organization.AdminOrganizationController;
import com.example.traning.periodization.TrainerPeriodizationController;
import com.example.traning.smarttrainer.controller.AdminMasterController;
import com.example.traning.trainer.TrainerAdviceController;
import com.example.traning.training.controller.api.TrainingApiController;
import com.example.traning.user.controller.AdminController;
import com.example.traning.user.controller.AdminRestoreController;
import com.example.traning.user.controller.AdminWithdrawalController;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 機能見直し-1-#15: 役割×操作の組み合わせで、コントローラーの{@code @PreAuthorize}（メソッド＞クラスの優先順位を含む）が
 * 実行時と同じ判定になることを検証する。実行時と同じ {@link PreAuthorizeAuthorizationManager} で評価する。
 *
 * <p>トレーナーは会員の記録の閲覧画面・APIと指導機能（アドバイス・期分け）にだけ到達でき、管理操作には到達できないこと （認可のすり抜けが無いこと）を重点的に確かめる。
 * 担当／担当外／範囲外の判定はサービス・{@code MemberAccessPolicy}側のテストで検証する。
 */
class RoleAuthorizationMatrixTest {

  private static final PreAuthorizeAuthorizationManager MANAGER =
      new PreAuthorizeAuthorizationManager();

  private static boolean granted(Class<?> controller, String methodName, String role) {
    Method method =
        Arrays.stream(controller.getDeclaredMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new AssertionError(controller.getSimpleName() + "#" + methodName));
    Authentication auth = new TestingAuthenticationToken("u", "p", "ROLE_" + role);
    AuthorizationResult result =
        MANAGER.authorize(() -> auth, new SimpleMethodInvocation(instance(controller), method));
    // 注釈が無いメソッドは null（=メソッドセキュリティでは判定しない）。管理系では必ず注釈がある前提なので拒否扱いにする
    return result != null && result.isGranted();
  }

  /** 評価には対象オブジェクトが必要なため、コンストラクタを通さずに生成する（依存は使わない）。 */
  private static Object instance(Class<?> controller) {
    return new org.objenesis.ObjenesisStd().newInstance(controller);
  }

  /** リクエストを受けるメソッド（@GetMapping 等が付いた public メソッド）の名前一覧。 */
  private static List<String> handlerMethods(Class<?> controller) {
    return Arrays.stream(controller.getDeclaredMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()))
        .filter(
            m ->
                m.isAnnotationPresent(GetMapping.class)
                    || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class)
                    || m.isAnnotationPresent(RequestMapping.class))
        .map(Method::getName)
        .distinct()
        .toList();
  }

  // ── トレーナーが到達できない管理操作（コントローラー全体） ───────────────────────

  static Stream<Arguments> adminOnlyControllers() {
    return Stream.of(
            AdminWithdrawalController.class,
            AdminRestoreController.class,
            AuditLogController.class,
            AdminMasterController.class,
            AdminInviteCodeController.class,
            AdminOrganizationController.class,
            AdminGymSettingController.class)
        .flatMap(c -> handlerMethods(c).stream().map(m -> Arguments.of(c, m)));
  }

  @ParameterizedTest(name = "TRAINERは{0}#{1}に到達できない")
  @MethodSource("adminOnlyControllers")
  void トレーナーは管理画面のどの操作にも到達できない(Class<?> controller, String method) {
    assertThat(granted(controller, method, "TRAINER")).isFalse();
    assertThat(granted(controller, method, "USER")).isFalse();
  }

  static Stream<String> adminControllerAdminOnlyMethods() {
    return Stream.of(
        "listUsers", "showEditUser", "updateUser", "exportUserCsv", "mfaReset", "reviewRole");
  }

  @ParameterizedTest(name = "TRAINERはAdminController#{0}に到達できない")
  @MethodSource("adminControllerAdminOnlyMethods")
  void トレーナーは会員の編集やMFAリセットや役割の任命に到達できない(String method) {
    assertThat(granted(AdminController.class, method, "TRAINER")).isFalse();
    assertThat(granted(AdminController.class, method, "USER")).isFalse();
  }

  @ParameterizedTest(name = "AdminControllerの{0}以外の受付メソッドはTRAINER不可（新しいメソッドの付け忘れ検知）")
  @MethodSource("adminControllerHandlers")
  void AdminControllerでトレーナーが到達できるのは記録の閲覧だけ(String method) {
    List<String> recordView =
        List.of(
            "showAllUsersTrainingList",
            "showUserTrainingDetail",
            "getTrainingDetails",
            "getChartData");
    assertThat(granted(AdminController.class, method, "TRAINER"))
        .isEqualTo(recordView.contains(method));
  }

  static Stream<String> adminControllerHandlers() {
    return handlerMethods(AdminController.class).stream();
  }

  @ParameterizedTest(name = "TRAINERはNoticeController#{0}（お知らせの管理）に到達できない")
  @MethodSource("noticeManageMethods")
  void トレーナーはお知らせの管理に到達できない(String method) {
    assertThat(granted(NoticeController.class, method, "TRAINER")).isFalse();
    assertThat(granted(NoticeController.class, method, "STORE_ADMIN")).isTrue();
  }

  static Stream<String> noticeManageMethods() {
    return Stream.of("manage", "create", "delete");
  }

  // ── トレーナーが到達できる画面（中で担当／担当外を判定） ───────────────────────

  @ParameterizedTest(name = "TRAINERは{0}#{1}に到達できる")
  @MethodSource("trainerReachable")
  void トレーナーは記録の閲覧と指導機能に到達できる(Class<?> controller, String method) {
    assertThat(granted(controller, method, "TRAINER")).isTrue();
    assertThat(granted(controller, method, "USER")).isFalse();
  }

  static Stream<Arguments> trainerReachable() {
    return Stream.of(
        Arguments.of(AdminController.class, "showAllUsersTrainingList"),
        Arguments.of(AdminController.class, "showUserTrainingDetail"),
        Arguments.of(AdminController.class, "getTrainingDetails"),
        Arguments.of(AdminController.class, "getChartData"),
        Arguments.of(TrainingApiController.class, "getDetails"),
        Arguments.of(TrainingApiController.class, "getTrainingVolume"),
        Arguments.of(TrainerAdviceController.class, "index"),
        Arguments.of(TrainerAdviceController.class, "send"),
        Arguments.of(TrainerAdviceController.class, "draft"),
        Arguments.of(TrainerAdviceController.class, "delete"),
        Arguments.of(TrainerPeriodizationController.class, "index"),
        Arguments.of(TrainerPeriodizationController.class, "send"),
        Arguments.of(MemberFollowController.class, "index"),
        Arguments.of(MemberFollowController.class, "acknowledge"));
  }

  @org.junit.jupiter.api.Test
  void トレーナーは担当の割り当て変更に到達できない_店舗管理者とジム管理者はできる() {
    assertThat(granted(TrainerAdviceController.class, "reassign", "TRAINER")).isFalse();
    assertThat(granted(TrainerAdviceController.class, "reassign", "STORE_ADMIN")).isTrue();
    assertThat(granted(TrainerAdviceController.class, "reassign", "ORG_ADMIN")).isTrue();
  }

  // ── 管理者（今の権限を維持＋任命・設定はジム管理者のみ） ───────────────────────

  @org.junit.jupiter.api.Test
  void 店舗管理者は今までどおり管理操作ができる() {
    for (String method : List.of("listUsers", "showEditUser", "updateUser", "mfaReset")) {
      assertThat(granted(AdminController.class, method, "STORE_ADMIN")).as(method).isTrue();
    }
    for (Class<?> c :
        List.of(
            AdminWithdrawalController.class,
            AdminRestoreController.class,
            AuditLogController.class,
            AdminMasterController.class)) {
      for (String method : handlerMethods(c)) {
        assertThat(granted(c, method, "STORE_ADMIN")).as(c.getSimpleName() + "#" + method).isTrue();
      }
    }
  }

  @org.junit.jupiter.api.Test
  void 役割の任命とトレーナー用の招待コードとジムの設定はジム管理者とシステム管理者だけ() {
    assertThat(granted(AdminController.class, "reviewRole", "STORE_ADMIN")).isFalse();
    assertThat(granted(AdminController.class, "reviewRole", "ORG_ADMIN")).isTrue();
    assertThat(granted(AdminController.class, "reviewRole", "ADMIN")).isTrue();
    for (Class<?> c : List.of(AdminInviteCodeController.class, AdminGymSettingController.class)) {
      for (String method : handlerMethods(c)) {
        assertThat(granted(c, method, "STORE_ADMIN"))
            .as(c.getSimpleName() + "#" + method)
            .isFalse();
        assertThat(granted(c, method, "ORG_ADMIN")).as(c.getSimpleName() + "#" + method).isTrue();
        assertThat(granted(c, method, "ADMIN")).as(c.getSimpleName() + "#" + method).isTrue();
      }
    }
  }

  // ── 機能見直し-1-#12 担当会員の一覧 ───────────────────────

  @org.junit.jupiter.api.Test
  void 担当会員の一覧はスタッフ全員が開け会員は開けない() {
    for (String method : handlerMethods(MemberFollowController.class)) {
      for (String role : List.of("TRAINER", "STORE_ADMIN", "ORG_ADMIN", "ADMIN")) {
        assertThat(granted(MemberFollowController.class, method, role))
            .as(method + " " + role)
            .isTrue();
      }
      assertThat(granted(MemberFollowController.class, method, "USER")).as(method).isFalse();
    }
  }
}
