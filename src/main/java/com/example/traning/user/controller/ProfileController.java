package com.example.traning.user.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.restpreference.RestPreferenceService;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.User;
import com.example.traning.user.form.ProfileForm;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/profile")
public class ProfileController {

  private final UserService userService;
  private final ProfileService profileService;
  private final RestPreferenceService restPreferenceService;

  public ProfileController(
      UserService userService,
      ProfileService profileService,
      RestPreferenceService restPreferenceService) {
    this.userService = userService;
    this.profileService = profileService;
    this.restPreferenceService = restPreferenceService;
  }

  @GetMapping
  public String showProfile(Model model, Principal principal) {
    User user = userService.getUserByEmail(principal.getName());
    ProfileForm form = new ProfileForm();
    form.setUserName(user.getUserName());
    form.setHeightCm(user.getHeightCm());
    form.setWeightKg(user.getWeightKg());
    form.setGender(user.getGender());
    form.setBirthDate(user.getBirthDate());
    model.addAttribute("profileForm", form);
    model.addAttribute("loginUser", user);
    model.addAttribute("user", user);
    model.addAttribute("goalModes", GoalMode.values());
    model.addAttribute(
        "restPreferences", restPreferenceService.listByUserId(user.getUserId().longValue()));
    model.addAttribute("defaultRestSeconds", RestPreferenceService.DEFAULT_REST_SECONDS);
    return "user/profile";
  }

  /** 17番: 種目別レスト時間の登録・更新。 */
  @AuditLog(action = "PROFILE_REST_TIME_UPSERT", targetTable = "user_item_rest_preferences")
  @PostMapping("/rest-time")
  public String upsertRestTime(
      @RequestParam("itemName") String itemName,
      @RequestParam("restSeconds") int restSeconds,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User user = userService.getUserByEmail(principal.getName());
    try {
      restPreferenceService.upsert(user.getUserId().longValue(), itemName, restSeconds);
      redirectAttributes.addFlashAttribute("successMessage", itemName + "のレスト時間を保存しました");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/user/profile";
  }

  /** 17番: 種目別レスト時間の登録削除（デフォルト2:00に戻す）。 */
  @AuditLog(action = "PROFILE_REST_TIME_DELETE", targetTable = "user_item_rest_preferences")
  @PostMapping("/rest-time/delete")
  public String deleteRestTime(
      @RequestParam("itemName") String itemName,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User user = userService.getUserByEmail(principal.getName());
    restPreferenceService.delete(user.getUserId().longValue(), itemName);
    redirectAttributes.addFlashAttribute("successMessage", itemName + "のレスト時間登録を削除しました");
    return "redirect:/user/profile";
  }

  /** F3 Phase1: 目的モード（筋肥大/減量/維持）の切替。 */
  @AuditLog(action = "PROFILE_GOAL_MODE_UPDATE", targetTable = "users")
  @PostMapping("/goal-mode")
  public String updateGoalMode(
      @RequestParam("goalMode") String goalMode,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User user = userService.getUserByEmail(principal.getName());
    profileService.updateGoalMode(user.getUserId(), GoalMode.fromString(goalMode).name());
    redirectAttributes.addFlashAttribute("successMessage", "目的モードを変更しました");
    return "redirect:/user/profile";
  }

  @AuditLog(action = "PROFILE_UPDATE", targetTable = "users")
  @PostMapping
  public String updateProfile(
      @Validated @ModelAttribute("profileForm") ProfileForm form,
      BindingResult result,
      Principal principal,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
      User user = userService.getUserByEmail(principal.getName());
      model.addAttribute("loginUser", user);
      model.addAttribute("user", user);
      model.addAttribute("goalModes", GoalMode.values());
      return "user/profile";
    }
    profileService.updateProfile(principal.getName(), form);
    redirectAttributes.addFlashAttribute("successMessage", "プロフィールを更新しました");
    return "redirect:/user/profile";
  }
}
