package com.example.traning.organization;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
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
 * 組織・店舗の管理画面（ita1-1 未実施分）。
 *
 * <p>GYM新規作成はROLE_ADMINのみ、STORE新規作成はROLE_ADMIN・ROLE_ORG_ADMIN（自組織配下のみ）。
 * ROLE_STORE_ADMINはこの画面自体にアクセスできない。
 */
@Controller
@RequestMapping("/admin/organizations")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN')")
public class AdminOrganizationController {

  private final OrganizationService organizationService;
  private final UserService userService;

  public AdminOrganizationController(
      OrganizationService organizationService, UserService userService) {
    this.organizationService = organizationService;
    this.userService = userService;
  }

  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  @GetMapping
  public String list(Model model) {
    User currentAdmin = getCurrentAdminUser();
    model.addAttribute("tree", organizationService.getOrganizationTree(currentAdmin));
    model.addAttribute("isAdmin", Role.fromValue(currentAdmin.getRole()) == Role.ADMIN);
    return "admin/organization_list";
  }

  @AuditLog(action = "ADMIN_ORGANIZATION_CREATE_GYM", targetTable = "organizations")
  @PostMapping("/gym")
  public String createGym(@RequestParam String name, RedirectAttributes redirectAttributes) {
    organizationService.createGym(name, getCurrentAdminUser());
    redirectAttributes.addFlashAttribute("successMessage", "組織を作成しました。");
    return "redirect:/admin/organizations";
  }

  @AuditLog(action = "ADMIN_ORGANIZATION_CREATE_STORE", targetTable = "organizations")
  @PostMapping("/store")
  public String createStore(
      @RequestParam String name,
      @RequestParam Long parentGymId,
      RedirectAttributes redirectAttributes) {
    organizationService.createStore(name, parentGymId, getCurrentAdminUser());
    redirectAttributes.addFlashAttribute("successMessage", "店舗を登録しました。");
    return "redirect:/admin/organizations";
  }

  @AuditLog(action = "ADMIN_ORGANIZATION_RENAME", targetTable = "organizations")
  @PostMapping("/{id}/rename")
  public String rename(
      @PathVariable Long id, @RequestParam String name, RedirectAttributes redirectAttributes) {
    organizationService.renameOrganization(id, name, getCurrentAdminUser());
    redirectAttributes.addFlashAttribute("successMessage", "更新しました。");
    return "redirect:/admin/organizations";
  }
}
