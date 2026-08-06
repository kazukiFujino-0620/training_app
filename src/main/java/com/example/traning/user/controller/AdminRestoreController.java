package com.example.traning.user.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.service.AdminRestoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/deleted")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminRestoreController {

  private final AdminRestoreService adminRestoreService;

  public AdminRestoreController(AdminRestoreService adminRestoreService) {
    this.adminRestoreService = adminRestoreService;
  }

  @GetMapping("/users")
  public String listDeletedUsers(Model model) {
    model.addAttribute("deletedUsers", adminRestoreService.findDeletedUsers());
    return "admin/deleted_users";
  }

  @GetMapping("/trainings")
  public String listDeletedTrainings(Model model) {
    model.addAttribute("deletedTrainings", adminRestoreService.findDeletedTrainings());
    return "admin/deleted_trainings";
  }

  @AuditLog(action = "ADMIN_USER_RESTORE", targetTable = "users")
  @PostMapping("/users/{id}/restore")
  public String restoreUser(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
    adminRestoreService.restoreUser(id);
    log.info("Admin restored soft-deleted user - userId: {}", id);
    redirectAttributes.addFlashAttribute("successMessage", "ユーザーを復元しました。");
    return "redirect:/admin/deleted/users";
  }

  @AuditLog(action = "ADMIN_TRAINING_RESTORE", targetTable = "trainings")
  @PostMapping("/trainings/{id}/restore")
  public String restoreTraining(
      @PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
    adminRestoreService.restoreTraining(id);
    log.info("Admin restored soft-deleted training - trainingId: {}", id);
    redirectAttributes.addFlashAttribute("successMessage", "トレーニング記録を復元しました。");
    return "redirect:/admin/deleted/trainings";
  }
}
