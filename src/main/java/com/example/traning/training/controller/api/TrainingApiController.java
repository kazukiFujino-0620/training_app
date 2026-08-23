package com.example.traning.training.controller.api;

import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** トレーニング関連 REST API コントローラー。 */
@RestController
@Slf4j
public class TrainingApiController {

  private final TrainingService trainingService;
  private final UserService userService;
  private final OrganizationScopeResolver organizationScopeResolver;

  public TrainingApiController(
      TrainingService trainingService,
      UserService userService,
      OrganizationScopeResolver organizationScopeResolver) {
    this.trainingService = trainingService;
    this.userService = userService;
    this.organizationScopeResolver = organizationScopeResolver;
  }

  /**
   * 指定ユーザー・日付のトレーニング詳細を取得する（管理者用）。
   *
   * <p>以前は SecurityConfig の URL マッチングのみに依存しており、 設定の変更や別コントローラーへの移動で認可が外れるリスクがあった。
   *
   * <p>ita1-1 フェーズ3: userId 指定時はアクセス可能組織かどうかを検証（IDOR対策）。 userId 未指定時（全ユーザー当日データ）は ROLE_ADMIN
   * 以外はアクセス可能な組織でサーバー側絞り込みを行う。
   */
  @GetMapping("/admin/api/training-details")
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN', 'TRAINER')")
  public List<TrainingDetail> getDetails(
      @RequestParam String date, @RequestParam(required = false) Long userId) {

    log.info("管理者: トレーニング詳細取得 userId={}, date={}", userId, date);

    if (userId != null) {
      User targetUser = userService.getUserById(userId.intValue());
      assertAccessible(targetUser);
      return trainingService.findByUserIdAndDate(userId, date);
    }

    Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(getCurrentAdminUser());
    return trainingService.findByDate(date, accessibleOrganizationIds);
  }

  /**
   * 指定ユーザーのトレーニングボリューム（部位別・期間別）を取得する（管理者用）。
   *
   * <p>userId をパスパラメータで受け取るため、一般ユーザーが他ユーザーの データへアクセスできる IDOR 脆弱性を URLレベル + メソッドレベルの
   * 二重チェックで防止する。ita1-1 フェーズ3: 組織スコープによるIDOR対策を追加。
   */
  @GetMapping("/admin/api/training-volume/{userId}")
  @ResponseBody
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN', 'TRAINER')")
  public Map<String, Object> getTrainingVolume(
      @PathVariable Long userId, @RequestParam String startDate, @RequestParam String endDate) {

    log.info("管理者: ボリュームデータ取得 userId={}, {} ~ {}", userId, startDate, endDate);

    User targetUser = userService.getUserById(userId.intValue());
    assertAccessible(targetUser);

    return trainingService.makeChartDataCustom(userId, startDate, endDate);
  }

  // ── private helper ────────────────────────────────────────────────────

  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  private void assertAccessible(User targetUser) {
    User currentAdmin = getCurrentAdminUser();
    if (!organizationScopeResolver.canAccessOrganization(
        currentAdmin, targetUser.getOrganizationId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "対象のユーザーにアクセスする権限がありません");
    }
  }
}
