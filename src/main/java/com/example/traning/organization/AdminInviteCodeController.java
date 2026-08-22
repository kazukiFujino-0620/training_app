package com.example.traning.organization;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 招待コードの発行・一覧・失効画面（ita1-1 未実施分）。
 *
 * <p>発行できるのはROLE_ADMIN（全組織向け）・ROLE_ORG_ADMIN（自組織向けのみ）。 ROLE_STORE_ADMINはこの画面自体にアクセスできない。
 */
@Controller
@RequestMapping("/admin/invite-codes")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN')")
public class AdminInviteCodeController {

  private final InviteCodeService inviteCodeService;
  private final OrganizationDao organizationDao;
  private final OrganizationScopeResolver organizationScopeResolver;
  private final UserService userService;

  public AdminInviteCodeController(
      InviteCodeService inviteCodeService,
      OrganizationDao organizationDao,
      OrganizationScopeResolver organizationScopeResolver,
      UserService userService) {
    this.inviteCodeService = inviteCodeService;
    this.organizationDao = organizationDao;
    this.organizationScopeResolver = organizationScopeResolver;
    this.userService = userService;
  }

  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  private List<Organization> assignableOrganizations(User currentAdmin) {
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(currentAdmin);
    if (accessible == null) {
      return organizationDao.selectAll();
    }
    return organizationDao.selectByIds(new ArrayList<>(accessible));
  }

  @GetMapping
  public String list(Model model) {
    User currentAdmin = getCurrentAdminUser();
    List<Organization> organizations = assignableOrganizations(currentAdmin);
    model.addAttribute("codes", inviteCodeService.listForAdmin(currentAdmin));
    model.addAttribute("organizations", organizations);
    model.addAttribute(
        "organizationNames",
        organizations.stream()
            .collect(Collectors.toMap(Organization::getId, Organization::getName)));
    return "admin/invite_code_list";
  }

  @AuditLog(action = "ADMIN_INVITE_CODE_ISSUE", targetTable = "invite_codes")
  @PostMapping
  public String issue(
      @RequestParam Long organizationId,
      @RequestParam(required = false) String expiresAt,
      @RequestParam(required = false) Integer maxUses,
      RedirectAttributes redirectAttributes) {
    LocalDateTime expiry =
        (expiresAt != null && !expiresAt.isBlank())
            ? LocalDate.parse(expiresAt).atTime(23, 59, 59)
            : null;
    inviteCodeService.issue(organizationId, expiry, maxUses, getCurrentAdminUser());
    redirectAttributes.addFlashAttribute("successMessage", "招待コードを発行しました。");
    return "redirect:/admin/invite-codes";
  }

  @AuditLog(action = "ADMIN_INVITE_CODE_REVOKE", targetTable = "invite_codes")
  @PostMapping("/{id}/revoke")
  public String revoke(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    inviteCodeService.revoke(id, getCurrentAdminUser());
    redirectAttributes.addFlashAttribute("successMessage", "招待コードを失効させました。");
    return "redirect:/admin/invite-codes";
  }
}
