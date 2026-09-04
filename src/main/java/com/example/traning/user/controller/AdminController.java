package com.example.traning.user.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.body.BodyMeasurement;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.common.WebErrorCode;
import com.example.traning.export.DataExportService;
import com.example.traning.mfa.MfaService;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.OrganizationType;
import com.example.traning.organization.UserStoreAccess;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.form.UserAdminUpdateForm;
import com.example.traning.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 管理者専用コントローラー。
 *
 * <p>★ 修正ポイント（指摘1・3対応） クラスレベルに @PreAuthorize を付与することで、 このコントローラーの全メソッドに管理者権限チェックを一括適用する。
 * SecurityConfig の URL パターン設定と合わせた多層防御。
 * ita1-1フェーズ3対応でROLE_ORG_ADMIN/ROLE_STORE_ADMINも許可（自組織/自店舗スコープでの利用を想定。
 * データの絞り込み自体はOrganizationScopeResolverによるサービス層フィルタリングで行う）。
 *
 * <p>また、userId をパスパラメータや @RequestParam で受け取るエンドポイントでは ユーザー存在チェックを行い、存在しない ID へのアクセスに対して
 * 適切なエラーを返すよう修正（IDOR の影響範囲を縮小）。
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN')")
@Slf4j
public class AdminController {

  private final UserService userService;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final CalorieCalculator calorieCalculator;
  private final MfaService mfaService;
  private final DataExportService dataExportService;
  private final BodyMeasurementService bodyMeasurementService;
  private final OrganizationScopeResolver organizationScopeResolver;
  private final OrganizationDao organizationDao;
  private final UserStoreAccessDao userStoreAccessDao;

  public AdminController(
      UserService userService,
      TrainingDao trainingDao,
      TrainingDetailDao trainingDetailDao,
      CalorieCalculator calorieCalculator,
      MfaService mfaService,
      DataExportService dataExportService,
      BodyMeasurementService bodyMeasurementService,
      OrganizationScopeResolver organizationScopeResolver,
      OrganizationDao organizationDao,
      UserStoreAccessDao userStoreAccessDao) {
    this.userService = userService;
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.calorieCalculator = calorieCalculator;
    this.mfaService = mfaService;
    this.dataExportService = dataExportService;
    this.bodyMeasurementService = bodyMeasurementService;
    this.organizationScopeResolver = organizationScopeResolver;
    this.organizationDao = organizationDao;
    this.userStoreAccessDao = userStoreAccessDao;
  }

  /** ユーザー編集画面でのロール選択肢（value・表示ラベル）。 */
  public record RoleOption(String value, String label) {}

  /** ログイン中の管理者ユーザーを取得する。 */
  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  /** 対象ユーザーが現在の管理者からアクセス可能な組織に属するか検証する。 属さない場合は403を返してIDORを防止する（ROLE_ADMINは全組織アクセス可のため常に許可）。 */
  private void assertAccessible(User targetUser) {
    User currentAdmin = getCurrentAdminUser();
    if (!organizationScopeResolver.canAccessOrganization(
        currentAdmin, targetUser.getOrganizationId())) {
      log.warn(
          "組織外アクセス拒否: 管理者={}, 対象ユーザー={}, 対象組織={}",
          currentAdmin.getEmail(),
          targetUser.getUserId(),
          targetUser.getOrganizationId());
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.FORBIDDEN, "対象のユーザーにアクセスする権限がありません");
    }
  }

  /** ユーザー一覧画面を表示する。ROLE_ADMIN以外は自身がアクセス可能な組織のユーザーのみに絞り込む。 */
  @GetMapping("/users")
  public String listUsers(Model model) {
    model.addAttribute("userList", filterByAccessibleOrganizations(userService.findAll()));
    return "admin/user_list";
  }

  /**
   * アクセス可能な組織（ROLE_ADMINはnull＝全組織）でユーザー一覧を絞り込む。
   *
   * <p>ROLE_ADMIN（全組織アクセス可）であっても、一般ユーザー（招待コードなし登録・デフォルト組織所属）は
   * 通常のジム運営画面のクエリからは除外する（セキュリティ対応）。ADMIN権限侵害時の被害範囲を 最小化する目的であり、DB直接アクセス等の技術的な閲覧経路までは塞がない。
   */
  private List<User> filterByAccessibleOrganizations(List<User> users) {
    java.util.Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(getCurrentAdminUser());
    if (accessibleOrganizationIds == null) {
      return users.stream()
          .filter(u -> !isGeneralUserOrganization(u.getOrganizationId()))
          .collect(Collectors.toList());
    }
    return users.stream()
        .filter(u -> accessibleOrganizationIds.contains(u.getOrganizationId()))
        .collect(Collectors.toList());
  }

  private boolean isGeneralUserOrganization(Long organizationId) {
    return organizationId != null && organizationId == Organization.DEFAULT_STORE_ORGANIZATION_ID;
  }

  /**
   * ユーザー編集画面を表示する。
   *
   * <p>★ getUserById は存在しない ID の場合 RuntimeException をスローするため、 存在しないユーザーIDを指定した場合は自動的に 500 →
   * 適切なハンドラがあれば 404 になる。 GlobalExceptionHandler で RuntimeException → 404 へマッピングすることを推奨。
   */
  @GetMapping("/user/edit/{id}")
  public String showEditUser(@PathVariable("id") Integer id, Model model) {
    User user = userService.getUserById(id); // 存在しなければ RuntimeException
    assertAccessible(user);
    User currentAdmin = getCurrentAdminUser();
    Role adminRole = Role.fromValue(currentAdmin.getRole());

    model.addAttribute("user", user);
    model.addAttribute("canManageRole", adminRole != Role.STORE_ADMIN);
    model.addAttribute("assignableRoles", assignableRoles(adminRole));
    model.addAttribute("isAdmin", adminRole == Role.ADMIN);
    model.addAttribute(
        "organizations", adminRole == Role.ADMIN ? organizationDao.selectAll() : List.of());
    model.addAttribute("assignableStores", assignableStores(currentAdmin, adminRole));
    model.addAttribute(
        "currentStoreAssignments",
        userStoreAccessDao.selectStoreOrganizationIdsByUserId(user.getUserId().longValue()));
    return "admin/user_edit";
  }

  /** 操作者の権限に応じて、選べるロールの選択肢だけを生成する（STORE_ADMIN/ADMINへの権限昇格の連鎖を防止）。 */
  private List<RoleOption> assignableRoles(Role adminRole) {
    if (adminRole == Role.ADMIN) {
      return List.of(
          new RoleOption(Role.USER.value(), "一般ユーザー（USER）"),
          new RoleOption(Role.STORE_ADMIN.value(), "店舗管理者（STORE_ADMIN）"),
          new RoleOption(Role.ORG_ADMIN.value(), "組織管理者（ORG_ADMIN）"),
          new RoleOption(Role.ADMIN.value(), "システム管理者（ADMIN）"));
    }
    if (adminRole == Role.ORG_ADMIN) {
      return List.of(
          new RoleOption(Role.USER.value(), "一般ユーザー（USER）"),
          new RoleOption(Role.STORE_ADMIN.value(), "店舗管理者（STORE_ADMIN）"));
    }
    return List.of();
  }

  /** 店舗兼任セクションで選べる店舗一覧。ADMINは全店舗、ORG_ADMINは自組織配下の店舗のみ。 */
  private List<Organization> assignableStores(User currentAdmin, Role adminRole) {
    if (adminRole == Role.ADMIN) {
      return organizationDao.selectAll().stream()
          .filter(o -> OrganizationType.STORE.name().equals(o.getType()))
          .collect(Collectors.toList());
    }
    if (adminRole == Role.ORG_ADMIN) {
      return organizationDao.selectByParentOrganizationId(currentAdmin.getOrganizationId());
    }
    return List.of();
  }

  /**
   * ユーザー情報更新。 UserAdminUpdateForm を使い userName / role / enabled / organizationId / storeAssignments
   * を更新する。password はこのエンドポイントでは変更不可（Mass Assignment 防止）。
   *
   * <p>role・organizationId・storeAssignments の実際の反映可否は操作者の権限で制御する（ita1-1
   * 未実施分対応）。ROLE_ADMINは全範囲、ROLE_ORG_ADMINは自組織内でUSER⇔STORE_ADMINの付け替えのみ（ORG_ADMIN/ADMINへの昇格・組織自体の変更は不可）、
   * ROLE_STORE_ADMINはこのセクション自体を操作できない（画面側で非表示）。
   */
  @AuditLog(action = "ADMIN_USER_UPDATE", targetTable = "users")
  @Transactional
  @PostMapping("/user/update")
  public String updateUser(
      @Validated @ModelAttribute UserAdminUpdateForm form,
      BindingResult result,
      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
      redirectAttributes.addFlashAttribute("errorMessage", "入力内容に誤りがあります。再度ご確認ください。");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
      return "redirect:/admin/user/edit/" + form.getUserId();
    }
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String currentEmail = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    User existing = userService.getUserById(form.getUserId());
    assertAccessible(existing);
    // 自分自身を無効化できないよう防止
    if (currentEmail.equals(existing.getEmail()) && Boolean.FALSE.equals(form.getEnabled())) {
      redirectAttributes.addFlashAttribute("errorMessage", "自分自身のアカウントを無効にすることはできません。");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INVALID_STATE);
      return "redirect:/admin/user/edit/" + form.getUserId();
    }
    // 自分自身を管理者権限から降格できないよう防止（ロックアウト防止）
    if (currentEmail.equals(existing.getEmail())
        && Role.ADMIN.value().equals(existing.getRole())
        && !Role.ADMIN.value().equals(form.getRole())) {
      redirectAttributes.addFlashAttribute("errorMessage", "自分自身の管理者権限を外すことはできません。");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INVALID_STATE);
      return "redirect:/admin/user/edit/" + form.getUserId();
    }

    User currentAdmin = getCurrentAdminUser();
    Role adminRole = Role.fromValue(currentAdmin.getRole());
    if (!isRoleChangeAllowed(adminRole, form.getRole())) {
      redirectAttributes.addFlashAttribute("errorMessage", "その権限へ変更する権限がありません。");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INVALID_STATE);
      return "redirect:/admin/user/edit/" + form.getUserId();
    }

    // 所属組織の変更はROLE_ADMINのみ反映（ORG_ADMIN操作時は自組織のまま変更しない）
    Long newOrganizationId =
        (adminRole == Role.ADMIN && form.getOrganizationId() != null)
            ? form.getOrganizationId()
            : existing.getOrganizationId();

    User updatedUser =
        existing.toBuilder()
            .userName(form.getUserName())
            .role(form.getRole())
            .enabled(form.getEnabled())
            .organizationId(newOrganizationId)
            .updatedDatetime(LocalDateTime.now())
            .build();
    userService.updateUserInfo(updatedUser);

    replaceStoreAssignments(form, currentAdmin, adminRole, newOrganizationId);

    return "redirect:/admin/users";
  }

  /** 操作者の権限で指定ロールへの変更が許されるか判定する。ROLE_STORE_ADMINはこのセクション自体を操作できない。 */
  private boolean isRoleChangeAllowed(Role adminRole, String newRole) {
    if (adminRole == Role.ADMIN) {
      return true;
    }
    if (adminRole == Role.ORG_ADMIN) {
      Role target = Role.fromValue(newRole);
      return target == Role.USER || target == Role.STORE_ADMIN;
    }
    return false;
  }

  /**
   * 店舗兼任設定を洗い替える。対象ロールがROLE_STORE_ADMINの場合のみ意味を持つ（それ以外は全削除のみ行う）。
   * ORG_ADMINが操作する場合は自組織配下の店舗のみ登録を許可し、範囲外の値は無視する。
   */
  private void replaceStoreAssignments(
      UserAdminUpdateForm form, User currentAdmin, Role adminRole, Long targetOrganizationId) {
    userStoreAccessDao.deleteByUserId(form.getUserId().longValue());
    boolean supportsStoreAssignments = Role.STORE_ADMIN.value().equals(form.getRole());
    if (!supportsStoreAssignments || form.getStoreAssignments() == null) {
      return;
    }
    for (Long storeId : form.getStoreAssignments()) {
      if (storeId == null || storeId.equals(targetOrganizationId)) {
        continue; // 自店舗自体は兼任として登録しない
      }
      if (adminRole == Role.ORG_ADMIN
          && !organizationScopeResolver.canAccessOrganization(currentAdmin, storeId)) {
        continue; // 自組織配下以外は無視（不正な値の混入対策）
      }
      UserStoreAccess access = new UserStoreAccess();
      access.setUserId(form.getUserId().longValue());
      access.setStoreOrganizationId(storeId);
      userStoreAccessDao.insert(access);
    }
  }

  @GetMapping("/all-users-training")
  public String showAllUsersTrainingList(
      @RequestParam(name = "userName", required = false) String userName, Model model) {

    List<User> users;
    if (userName != null && !userName.isEmpty()) {
      users = userService.searchUsers(userName);
    } else {
      users = userService.findAll();
    }

    model.addAttribute("userList", filterByAccessibleOrganizations(users));
    model.addAttribute("userName", userName);
    return "admin/all_users_training_list";
  }

  /**
   * ユーザーのトレーニング詳細画面を表示する。
   *
   * <p>★ getUserById でユーザー存在を確認し、 存在しない ID を指定された場合は RuntimeException で処理を中断する。
   */
  @GetMapping("/user/training-detail/{id}")
  public String showUserTrainingDetail(
      @PathVariable("id") Integer id,
      @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      Model model) {

    log.info("詳細画面表示開始: ユーザーID = {}", id);

    // ★ ユーザー存在確認（存在しなければ RuntimeException をスロー）
    User user = userService.getUserById(id);
    assertAccessible(user);
    model.addAttribute("user", user);

    LocalDate targetMonth =
        (date != null) ? date.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1);

    List<LocalDate> dateList = userService.generateCalendarDates(targetMonth);
    List<String> dayStatusList = userService.getDayStatusList(id, dateList);

    model.addAttribute("targetMonth", targetMonth);
    model.addAttribute("prevMonth", targetMonth.minusMonths(1));
    model.addAttribute("nextMonth", targetMonth.plusMonths(1));
    model.addAttribute("dateList", dateList);
    model.addAttribute("status", dayStatusList);
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("userId", id);

    try {
      LocalDate endDate = LocalDate.now();
      LocalDate startDate = endDate.minusDays(30);

      List<TrainingDao.VolumeResult> volumeData =
          trainingDao.selectVolumeList(
              id.longValue(), null, startDate.toString(), endDate.toString());

      model.addAttribute("volumeData", volumeData);
    } catch (Exception e) {
      log.error("グラフデータ取得エラー: ユーザーID: {}", id, e);
      model.addAttribute("volumeData", List.of());
    }

    // プロフィール情報を使ったカロリー計算（管理者用: 代表トレーニング時間を使用）
    // user オブジェクトはすでに取得済み
    // 管理者画面はカレンダー+グラフ画面のため、カロリーは当月の平均的な値を出さずにユーザー情報のみ渡す
    model.addAttribute("targetUser", user);

    // ita4-4 (A) 追加対応: ORG_ADMIN/STORE_ADMINのみ、このユーザー・日付宛のアドバイス送信導線を表示する
    Role viewerRole = Role.fromValue(getCurrentAdminUser().getRole());
    model.addAttribute(
        "canSendAdvice", viewerRole == Role.ORG_ADMIN || viewerRole == Role.STORE_ADMIN);

    return "admin/user_training_detail";
  }

  /**
   * 指定ユーザー・日付のトレーニング詳細を取得する API（管理者用）。
   *
   * <p>★ @PreAuthorize はクラスレベルで適用済み。 加えて、受け取った userId でユーザー存在確認を実施し、 存在しない ID
   * を渡されても意図しないデータ操作が起きないようにする。
   */
  @GetMapping("/training-details")
  @ResponseBody
  public ResponseEntity<List<Training>> getTrainingDetails(
      @RequestParam("userId") Integer userId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

    log.info("API: トレーニング詳細取得 - ユーザーID: {}, 日付: {}", userId, date);

    // ★ ユーザー存在確認
    assertAccessible(userService.getUserById(userId)); // 存在しなければ RuntimeException → 404 推奨

    try {
      List<Training> trainings = trainingDao.selectByUserIdAndDate(userId, date);
      log.info("トレーニング基本データ取得: {} 件", trainings.size());

      for (Training training : trainings) {
        try {
          List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(training.getId());
          training.setDetails(details);
        } catch (Exception e) {
          log.error("トレーニングID {} の詳細取得エラー", training.getId(), e);
          training.setDetails(List.of());
        }
      }

      return ResponseEntity.ok(trainings);

    } catch (Exception e) {
      log.error("トレーニング詳細取得エラー: ユーザーID: {}, 日付: {}", userId, date, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  /**
   * 指定ユーザーのトレーニングボリュームをグラフ用に返す API（管理者用）。
   *
   * <p>★ @PreAuthorize はクラスレベルで適用済み。 ユーザー存在確認を追加し、IDOR の影響範囲を縮小。
   */
  @GetMapping("/chart-data")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getChartData(
      @RequestParam("userId") Integer userId,
      @RequestParam(name = "startDate", required = false) String startDate,
      @RequestParam(name = "endDate", required = false) String endDate) {

    log.info("API: グラフデータ取得 - ユーザーID: {}, 開始日: {}, 終了日: {}", userId, startDate, endDate);

    // ★ ユーザー存在確認
    assertAccessible(userService.getUserById(userId));

    try {
      LocalDate end = (endDate != null) ? LocalDate.parse(endDate) : LocalDate.now();
      LocalDate start = (startDate != null) ? LocalDate.parse(startDate) : end.minusDays(30);

      Map<String, List<TrainingDao.VolumeResult>> partData = new HashMap<>();
      String[] partCodes = {"CHEST", "BACK", "ARM", "SHOULDER", "LEG"};
      for (String partCode : partCodes) {
        partData.put(
            partCode,
            trainingDao.selectVolumeList(
                userId.longValue(), partCode, start.toString(), end.toString()));
      }

      Map<String, Object> chartData = new HashMap<>();
      List<String> labels = new ArrayList<>();
      List<Double> chest = new ArrayList<>();
      List<Double> back = new ArrayList<>();
      List<Double> arms = new ArrayList<>();
      List<Double> shoulders = new ArrayList<>();
      List<Double> legs = new ArrayList<>();

      long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
      for (int i = 0; i < daysBetween; i++) {
        labels.add(start.plusDays(i).toString());
        chest.add(0.0);
        back.add(0.0);
        arms.add(0.0);
        shoulders.add(0.0);
        legs.add(0.0);
      }

      fillVolumeList(partData.get("CHEST"), chest, start, daysBetween);
      fillVolumeList(partData.get("BACK"), back, start, daysBetween);
      fillVolumeList(partData.get("ARM"), arms, start, daysBetween);
      fillVolumeList(partData.get("SHOULDER"), shoulders, start, daysBetween);
      fillVolumeList(partData.get("LEG"), legs, start, daysBetween);

      chartData.put("labels", labels);
      chartData.put("chest", chest);
      chartData.put("back", back);
      chartData.put("arms", arms);
      chartData.put("shoulders", shoulders);
      chartData.put("legs", legs);

      log.info("グラフデータ取得完了 (期間: {} ~ {})", start, end);
      return ResponseEntity.ok(chartData);

    } catch (Exception e) {
      log.error("グラフデータ取得エラー: ユーザーID: {}", userId, e);
      return ResponseEntity.internalServerError().build();
    }
  }

  // ── データエクスポート ─────────────────────────────────────────────────

  @AuditLog(action = "ADMIN_DATA_EXPORT", targetTable = "users")
  @GetMapping("/user/{id}/export/csv")
  public void exportUserCsv(
      @PathVariable("id") Long id,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      HttpServletResponse response)
      throws IOException {

    assertAccessible(userService.getUserById(id.intValue())); // ユーザー存在確認＋組織チェック

    if (from.isAfter(to)) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "開始日は終了日以前の日付を指定してください");
      return;
    }

    String filename = String.format("training_data_%d_%s_%s.csv", id, from, to);
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

    List<BodyMeasurement> measurements = bodyMeasurementService.getForDateRange(id, from, to);
    dataExportService.writeCsv(id, from, to, measurements, response.getOutputStream());
  }

  // ── 2FA管理 ───────────────────────────────────────────────────────────

  @AuditLog(action = "ADMIN_MFA_RESET", targetTable = "user_mfa_settings")
  @PostMapping("/user/{id}/mfa/reset")
  public String mfaReset(
      @PathVariable("id") Integer id,
      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
    assertAccessible(userService.getUserById(id)); // 存在確認＋組織チェック
    mfaService.disableMfa(id.longValue());
    log.info("Admin MFA reset: userId={}", id);
    redirectAttributes.addFlashAttribute("successMessage", "2段階認証をリセットしました。");
    return "redirect:/admin/user/edit/" + id;
  }

  // ── private helper ────────────────────────────────────────────────────

  private void fillVolumeList(
      List<TrainingDao.VolumeResult> results,
      List<Double> target,
      LocalDate start,
      long daysBetween) {
    if (results == null) return;
    for (TrainingDao.VolumeResult data : results) {
      int index =
          (int)
              java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
      if (index >= 0 && index < daysBetween) {
        target.set(index, target.get(index) + data.totalVolume);
      }
    }
  }
}
