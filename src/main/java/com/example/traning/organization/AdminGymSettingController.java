package com.example.traning.organization;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ジムの設定画面（機能見直し-1-#15 仮画面D）。ジム管理者（自分のジム）とシステム管理者（全ジム）のみ。
 *
 * <p>今は「店舗管理者・ジム管理者が会員の健康に関わる情報を見られるか」（初期値: 見られない）。
 */
@Controller
@RequestMapping("/admin/gym-settings")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN')")
public class AdminGymSettingController {

  private final GymSettingService gymSettingService;
  private final UserService userService;

  public AdminGymSettingController(GymSettingService gymSettingService, UserService userService) {
    this.gymSettingService = gymSettingService;
    this.userService = userService;
  }

  private User currentUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }

  /**
   * @param gymId システム管理者がジムを選ぶ場合のみ（ジム管理者は自分のジムに固定）
   */
  @GetMapping
  public String show(@RequestParam(required = false) Long gymId, Model model) {
    List<Organization> gyms = gymSettingService.listEditableGyms(currentUser());
    Organization selected =
        gyms.stream()
            .filter(g -> gymId == null || g.getId().equals(gymId))
            .findFirst()
            .orElse(gyms.isEmpty() ? null : gyms.get(0));
    model.addAttribute("gyms", gyms);
    model.addAttribute("selectedGym", selected);
    model.addAttribute(
        "setting", selected != null ? gymSettingService.getForGym(selected.getId()) : null);
    return "admin/gym_settings";
  }

  @AuditLog(action = "ADMIN_GYM_SETTING_UPDATE", targetTable = "gym_settings")
  @PostMapping
  public String update(
      @RequestParam Long gymId,
      @RequestParam boolean staffHealthInfoVisible,
      RedirectAttributes redirectAttributes) {
    gymSettingService.updateStaffHealthInfoVisible(currentUser(), gymId, staffHealthInfoVisible);
    redirectAttributes.addFlashAttribute("successMessage", "ジムの設定を保存しました。");
    return "redirect:/admin/gym-settings?gymId=" + gymId;
  }
}
