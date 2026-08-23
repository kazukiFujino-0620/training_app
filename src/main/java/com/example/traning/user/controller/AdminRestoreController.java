package com.example.traning.user.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.training.Training;
import com.example.traning.user.User;
import com.example.traning.user.service.AdminRestoreService;
import com.example.traning.user.service.UserService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/deleted")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN', 'TRAINER')")
@Slf4j
public class AdminRestoreController {

  private final AdminRestoreService adminRestoreService;
  private final UserService userService;
  private final OrganizationScopeResolver organizationScopeResolver;

  public AdminRestoreController(
      AdminRestoreService adminRestoreService,
      UserService userService,
      OrganizationScopeResolver organizationScopeResolver) {
    this.adminRestoreService = adminRestoreService;
    this.userService = userService;
    this.organizationScopeResolver = organizationScopeResolver;
  }

  @GetMapping("/users")
  public String listDeletedUsers(Model model) {
    List<User> deletedUsers = adminRestoreService.findDeletedUsers();
    Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(getCurrentAdminUser());
    if (accessibleOrganizationIds != null) {
      deletedUsers =
          deletedUsers.stream()
              .filter(u -> accessibleOrganizationIds.contains(u.getOrganizationId()))
              .collect(Collectors.toList());
    }
    model.addAttribute("deletedUsers", deletedUsers);
    return "admin/deleted_users";
  }

  @GetMapping("/trainings")
  public String listDeletedTrainings(Model model) {
    List<Training> deletedTrainings = adminRestoreService.findDeletedTrainings();
    Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(getCurrentAdminUser());
    if (accessibleOrganizationIds != null) {
      deletedTrainings =
          deletedTrainings.stream()
              .filter(t -> accessibleOrganizationIds.contains(t.getOrganizationId()))
              .collect(Collectors.toList());
    }
    model.addAttribute("deletedTrainings", deletedTrainings);
    return "admin/deleted_trainings";
  }

  @AuditLog(action = "ADMIN_USER_RESTORE", targetTable = "users")
  @PostMapping("/users/{id}/restore")
  public String restoreUser(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
    assertUserAccessible(id);
    adminRestoreService.restoreUser(id);
    log.info("Admin restored soft-deleted user - userId: {}", id);
    redirectAttributes.addFlashAttribute("successMessage", "ユーザーを復元しました。");
    return "redirect:/admin/deleted/users";
  }

  @AuditLog(action = "ADMIN_TRAINING_RESTORE", targetTable = "trainings")
  @PostMapping("/trainings/{id}/restore")
  public String restoreTraining(
      @PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
    assertTrainingAccessible(id);
    adminRestoreService.restoreTraining(id);
    log.info("Admin restored soft-deleted training - trainingId: {}", id);
    redirectAttributes.addFlashAttribute("successMessage", "トレーニング記録を復元しました。");
    return "redirect:/admin/deleted/trainings";
  }

  // ── private helper ────────────────────────────────────────────────────

  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  /** IDOR対策: 対象ユーザーが現在の管理者のアクセス可能組織に属していなければ 403 を返す。 */
  private void assertUserAccessible(Integer targetUserId) {
    User currentAdmin = getCurrentAdminUser();
    Long targetOrganizationId = adminRestoreService.getUserOrganizationId(targetUserId);
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, targetOrganizationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "対象のユーザーにアクセスする権限がありません");
    }
  }

  /** IDOR対策: 対象トレーニングの所有ユーザーの組織が現在の管理者のアクセス可能組織になければ 403 を返す。 */
  private void assertTrainingAccessible(Long targetTrainingId) {
    User currentAdmin = getCurrentAdminUser();
    Long targetOrganizationId = adminRestoreService.getTrainingOrganizationId(targetTrainingId);
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, targetOrganizationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "対象のトレーニング記録にアクセスする権限がありません");
    }
  }
}
