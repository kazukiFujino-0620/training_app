package com.example.traning.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.body.BodyMeasurementService;
import com.example.traning.export.DataExportService;
import com.example.traning.mfa.MfaService;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.smarttrainer.recommendation.PartLoadService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.user.User;
import com.example.traning.user.form.UserAdminUpdateForm;
import com.example.traning.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * 機能見直し-1-#15: {@link AdminController} の記録の閲覧（トレーナー：担当／担当外・同じ店舗／他の店舗）と、 役割の選び直し（移行対象の扱い）を検証する。
 *
 * <p>組織: ジム1 → 店舗10・店舗20。トレーナー(id=1)は店舗10。スコープの解決は実物の {@link OrganizationScopeResolver}。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTrainerAccessTest {

  @Mock private UserService userService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private CalorieCalculator calorieCalculator;
  @Mock private MfaService mfaService;
  @Mock private DataExportService dataExportService;
  @Mock private BodyMeasurementService bodyMeasurementService;
  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;
  @Mock private UserDetails userDetails;
  @Mock private PartLoadService partLoadService;

  private AdminController controller;

  private static final long GYM_1 = 1L;
  private static final long STORE_10 = 10L;
  private static final long STORE_20 = 20L;

  private final User trainer = user(1, "ROLE_TRAINER", STORE_10, null);
  private final User mine = user(100, "ROLE_USER", STORE_10, 1L);
  private final User sameStoreOther = user(101, "ROLE_USER", STORE_10, 99L);
  private final User otherStore = user(102, "ROLE_USER", STORE_20, null);
  private final User staffSameStore = user(103, "ROLE_STORE_ADMIN", STORE_10, null);
  private final User mineInOtherStore = user(104, "ROLE_USER", STORE_20, 1L);

  @BeforeEach
  void setUp() {
    OrganizationScopeResolver resolver =
        new OrganizationScopeResolver(organizationDao, userStoreAccessDao);
    controller =
        new AdminController(
            userService,
            trainingDao,
            trainingDetailDao,
            calorieCalculator,
            mfaService,
            dataExportService,
            bodyMeasurementService,
            resolver,
            organizationDao,
            userStoreAccessDao,
            new MemberAccessPolicy(resolver),
            partLoadService);
    Organization s10 = new Organization();
    s10.setId(STORE_10);
    Organization s20 = new Organization();
    s20.setId(STORE_20);
    lenient()
        .when(organizationDao.selectByParentOrganizationId(GYM_1))
        .thenReturn(List.of(s10, s20));
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(anyLong()))
        .thenReturn(List.of());
    for (User u : List.of(mine, sameStoreOther, otherStore, staffSameStore, mineInOtherStore)) {
      lenient().when(userService.getUserById(u.getUserId())).thenReturn(u);
    }
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static User user(int id, String role, Long organizationId, Long assignedTrainerId) {
    return User.builder()
        .userId(id)
        .email("u" + id + "@example.com")
        .userName("u" + id)
        .role(role)
        .organizationId(organizationId)
        .assignedTrainerId(assignedTrainerId)
        .enabled(true)
        .build();
  }

  private void loginAs(User u) {
    lenient().when(userDetails.getUsername()).thenReturn(u.getEmail());
    lenient().when(userService.getUserByEmail(u.getEmail())).thenReturn(u);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
  }

  // ── トレーナーの一覧 ─────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void 一覧_トレーナーには担当会員と自分の店舗の会員だけが出て他の店舗とスタッフは出ない() {
    loginAs(trainer);
    when(userService.findAll())
        .thenReturn(
            List.of(mine, sameStoreOther, otherStore, staffSameStore, mineInOtherStore, trainer));

    Model model = new ExtendedModelMap();
    controller.showAllUsersTrainingList(null, model);

    assertThat((List<User>) model.getAttribute("userList"))
        .containsExactly(mine, sameStoreOther, mineInOtherStore);
    assertThat((Set<Integer>) model.getAttribute("assignedUserIds"))
        .containsExactlyInAnyOrder(100, 104);
    assertThat(model.getAttribute("isTrainerView")).isEqualTo(true);
  }

  @Test
  @SuppressWarnings("unchecked")
  void 一覧_名前で検索してもトレーナーの範囲で絞り込まれる() {
    loginAs(trainer);
    when(userService.searchUsers("u")).thenReturn(List.of(otherStore, sameStoreOther));

    Model model = new ExtendedModelMap();
    controller.showAllUsersTrainingList("u", model);

    assertThat((List<User>) model.getAttribute("userList")).containsExactly(sameStoreOther);
  }

  // ── 会員詳細 ─────────────────────────────────────────────

  @Test
  void 詳細_担当会員は記録の閲覧のみではなくアドバイスの導線も出る() {
    loginAs(trainer);
    Model model = new ExtendedModelMap();

    controller.showUserTrainingDetail(100, null, model);

    assertThat(model.getAttribute("recordsOnly")).isEqualTo(false);
    assertThat(model.getAttribute("canSendAdvice")).isEqualTo(true);
  }

  @Test
  void 詳細_同じ店舗の担当外の会員は記録の閲覧のみでアドバイスの導線は出ない() {
    loginAs(trainer);
    Model model = new ExtendedModelMap();

    controller.showUserTrainingDetail(101, null, model);

    assertThat(model.getAttribute("recordsOnly")).isEqualTo(true);
    assertThat(model.getAttribute("canSendAdvice")).isEqualTo(false);
  }

  @Test
  void 詳細とAPI_他の店舗の会員はトレーナーには403() {
    loginAs(trainer);
    assertThatThrownBy(() -> controller.showUserTrainingDetail(102, null, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> controller.getTrainingDetails(102, LocalDate.of(2026, 11, 14)))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> controller.getChartData(102, null, null))
        .isInstanceOf(ResponseStatusException.class);
    verify(trainingDao, never()).selectByUserIdAndDate(anyInt(), any());
  }

  @Test
  void 詳細とAPI_同じ店舗のスタッフの記録はトレーナーには403() {
    loginAs(trainer);
    assertThatThrownBy(() -> controller.showUserTrainingDetail(103, null, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> controller.getTrainingDetails(103, LocalDate.of(2026, 11, 14)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void API_同じ店舗の担当外の会員の記録はトレーナーも取得できる() {
    loginAs(trainer);
    when(trainingDao.selectByUserIdAndDate(101, LocalDate.of(2026, 11, 14))).thenReturn(List.of());

    assertThat(
            controller.getTrainingDetails(101, LocalDate.of(2026, 11, 14)).getStatusCode().value())
        .isEqualTo(200);
  }

  @Test
  void 詳細_店舗管理者は自店舗の会員を担当に関係なく見られて記録の閲覧のみにはならない() {
    loginAs(user(2, "ROLE_STORE_ADMIN", STORE_10, null));
    Model model = new ExtendedModelMap();

    controller.showUserTrainingDetail(101, null, model);

    assertThat(model.getAttribute("recordsOnly")).isEqualTo(false);
    assertThat(model.getAttribute("canSendAdvice")).isEqualTo(true);
  }

  // ── 部位別の直近負荷（機能見直し-1-#19） ─────────────────────────────

  private static final List<Map<String, Object>> PART_LOAD_ROWS =
      List.of(Map.of("partCode", "LEG", "partName", "脚", "volume", 7450L, "sets", 12, "pct", 80));

  @Test
  void 部位別の直近負荷_担当トレーナーには出る() {
    loginAs(trainer);
    when(partLoadService.loadRows(eq(100L), any())).thenReturn(PART_LOAD_ROWS);
    Model model = new ExtendedModelMap();

    controller.showUserTrainingDetail(100, null, model);

    assertThat(model.getAttribute("partLoadRows")).isEqualTo(PART_LOAD_ROWS);
  }

  @Test
  void 部位別の直近負荷_担当外のトレーナーには計算もせずモデルにも入れない() {
    loginAs(trainer);
    Model model = new ExtendedModelMap();

    controller.showUserTrainingDetail(101, null, model);

    assertThat(model.containsAttribute("partLoadRows")).isFalse();
    verify(partLoadService, never()).loadRows(anyLong(), any());
  }

  @Test
  void 部位別の直近負荷_店舗管理者ジム管理者システム管理者は担当に関係なく担当範囲の会員で出る() {
    when(partLoadService.loadRows(eq(101L), any())).thenReturn(PART_LOAD_ROWS);
    for (User viewer :
        List.of(
            user(2, "ROLE_STORE_ADMIN", STORE_10, null),
            user(3, "ROLE_ORG_ADMIN", GYM_1, null),
            user(4, "ROLE_ADMIN", 0L, null))) {
      loginAs(viewer);
      Model model = new ExtendedModelMap();

      controller.showUserTrainingDetail(101, null, model);

      assertThat(model.getAttribute("partLoadRows")).as(viewer.getRole()).isEqualTo(PART_LOAD_ROWS);
    }
  }

  @Test
  void 部位別の直近負荷_範囲外の会員は画面ごと403で計算しない() {
    loginAs(user(2, "ROLE_STORE_ADMIN", STORE_10, null));

    assertThatThrownBy(() -> controller.showUserTrainingDetail(102, null, new ExtendedModelMap()))
        .isInstanceOf(ResponseStatusException.class);
    verify(partLoadService, never()).loadRows(anyLong(), any());
  }

  // ── 役割の選び直し（移行対象） ────────────────────────────────────

  private final User orgAdmin = user(3, "ROLE_ORG_ADMIN", GYM_1, null);

  private User flaggedStaff(int id, Long organizationId) {
    User u = user(id, "ROLE_STORE_ADMIN", organizationId, null);
    u.setRoleReviewRequired(true);
    lenient().when(userService.getUserById(id)).thenReturn(u);
    return u;
  }

  @Test
  void 役割の選び直し_ジム管理者が要確認のスタッフをトレーナーにすると役割が変わり確認済みになり兼任店舗が消える() {
    loginAs(orgAdmin);
    flaggedStaff(200, STORE_10);
    RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

    String view = controller.reviewRole(200L, "ROLE_TRAINER", ra);

    assertThat(view).isEqualTo("redirect:/admin/users");
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userService).updateUserInfo(captor.capture());
    assertThat(captor.getValue().getRole()).isEqualTo("ROLE_TRAINER");
    verify(userService).updateRoleReviewRequired(200, false);
    verify(userStoreAccessDao).deleteByUserId(200L);
  }

  @Test
  void 役割の選び直し_店舗管理者のままを選ぶと確認済みになり兼任店舗は残る() {
    loginAs(orgAdmin);
    flaggedStaff(201, STORE_10);

    controller.reviewRole(201L, "ROLE_STORE_ADMIN", new RedirectAttributesModelMap());

    verify(userService).updateRoleReviewRequired(201, false);
    verify(userStoreAccessDao, never()).deleteByUserId(201L);
  }

  @Test
  void 役割の選び直し_ジム管理者やシステム管理者には昇格できない() {
    loginAs(orgAdmin);
    flaggedStaff(202, STORE_10);
    for (String role : List.of("ROLE_ORG_ADMIN", "ROLE_ADMIN", "ROLE_XXX")) {
      RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
      controller.reviewRole(202L, role, ra);
      assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    }
    verify(userService, never()).updateUserInfo(any());
    verify(userService, never()).updateRoleReviewRequired(anyInt(), anyBoolean());
  }

  @Test
  void 役割の選び直し_要確認でないユーザーはこの操作では変えられない() {
    loginAs(orgAdmin);
    RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

    controller.reviewRole(101L, "ROLE_TRAINER", ra);

    assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    verify(userService, never()).updateUserInfo(any());
  }

  @Test
  void 役割の選び直し_他のジムのスタッフは403() {
    loginAs(orgAdmin);
    flaggedStaff(203, 999L);

    assertThatThrownBy(
            () -> controller.reviewRole(203L, "ROLE_TRAINER", new RedirectAttributesModelMap()))
        .isInstanceOf(ResponseStatusException.class);
    verify(userService, never()).updateUserInfo(any());
  }

  @Test
  void 編集画面_ジム管理者はトレーナーを任命できて要確認なら確認済みになる() {
    loginAs(orgAdmin);
    flaggedStaff(204, STORE_10);
    UserAdminUpdateForm form = new UserAdminUpdateForm();
    form.setUserId(204);
    form.setUserName("u204");
    form.setRole("ROLE_TRAINER");
    form.setEnabled(true);

    controller.updateUser(
        form, new BeanPropertyBindingResult(form, "form"), new RedirectAttributesModelMap());

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userService).updateUserInfo(captor.capture());
    assertThat(captor.getValue().getRole()).isEqualTo("ROLE_TRAINER");
    verify(userService).updateRoleReviewRequired(204, false);
  }

  @Test
  void 編集画面_店舗管理者は役割を変えられない() {
    loginAs(user(2, "ROLE_STORE_ADMIN", STORE_10, null));
    UserAdminUpdateForm form = new UserAdminUpdateForm();
    form.setUserId(101);
    form.setUserName("u101");
    form.setRole("ROLE_TRAINER");
    form.setEnabled(true);
    RedirectAttributesModelMap ra = new RedirectAttributesModelMap();

    controller.updateUser(form, new BeanPropertyBindingResult(form, "form"), ra);

    assertThat(ra.getFlashAttributes()).containsKey("errorMessage");
    verify(userService, never()).updateUserInfo(any());
  }

  @Test
  void ユーザー一覧_要確認の件数はジム管理者に出て店舗管理者には出ない() {
    User flagged = flaggedStaff(205, STORE_10);
    when(userService.findAll()).thenReturn(List.of(flagged, sameStoreOther));

    loginAs(orgAdmin);
    Model model = new ExtendedModelMap();
    controller.listUsers(model);
    assertThat(model.getAttribute("roleReviewCount")).isEqualTo(1L);
    assertThat(model.getAttribute("canReviewRoles")).isEqualTo(true);

    loginAs(user(2, "ROLE_STORE_ADMIN", STORE_10, null));
    Model storeModel = new ExtendedModelMap();
    controller.listUsers(storeModel);
    assertThat(storeModel.getAttribute("roleReviewCount")).isEqualTo(0L);
    assertThat(storeModel.getAttribute("canReviewRoles")).isEqualTo(false);
  }
}
