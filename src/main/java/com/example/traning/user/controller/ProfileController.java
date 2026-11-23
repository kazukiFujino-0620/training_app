package com.example.traning.user.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.goal.FrequencyGoalService;
import com.example.traning.goal.GoalProposalService;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.User;
import com.example.traning.user.form.ProfileForm;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.time.LocalDateTime;
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
  private final GoalProposalService goalProposalService;
  private final FrequencyGoalService frequencyGoalService;

  public ProfileController(
      UserService userService,
      ProfileService profileService,
      GoalProposalService goalProposalService,
      FrequencyGoalService frequencyGoalService) {
    this.userService = userService;
    this.profileService = profileService;
    this.goalProposalService = goalProposalService;
    this.frequencyGoalService = frequencyGoalService;
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
    addGoalAttributes(model, user);
    return "user/profile";
  }

  /** 機能見直し-1-#20: 週の回数目標と「担当トレーナーと決めた目標」の表示（今の目標が承認した提案と同じ間だけ）。 */
  private void addGoalAttributes(Model model, User user) {
    Long userId = user.getUserId().longValue();
    model.addAttribute(
        "weeklyTargetCount", frequencyGoalService.findManualTarget(userId).orElse(null));
    model.addAttribute("goalAgreement", goalProposalService.agreementFor(userId).orElse(null));
  }

  /** F3 Phase1: 目的モード（筋肥大/減量/維持）の切替。 */
  @AuditLog(action = "PROFILE_GOAL_MODE_UPDATE", targetTable = "users")
  @PostMapping("/goal-mode")
  public String updateGoalMode(
      @RequestParam("goalMode") String goalMode,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User user = userService.getUserByEmail(principal.getName());
    // 機能見直し-1-#20: 値が変わったら「会員が変更」を記録する（担当トレーナーと決めた目標の表示も外れる）
    goalProposalService.updateGoalModeByMember(
        user.getUserId(), GoalMode.fromString(goalMode).name(), LocalDateTime.now());
    redirectAttributes.addFlashAttribute("successMessage", "目的モードを変更しました");
    return "redirect:/user/profile";
  }

  /** ita5-1: AI機能（トレーニング提案・疲労度分析・トレーナーアドバイス下書き）利用への同意の切替。 */
  @AuditLog(action = "PROFILE_AI_ADVICE_CONSENT_UPDATE", targetTable = "users")
  @PostMapping("/ai-advice-consent")
  public String updateAiAdviceConsent(
      @RequestParam(value = "aiAdviceConsent", required = false) Boolean aiAdviceConsent,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User user = userService.getUserByEmail(principal.getName());
    boolean consent = Boolean.TRUE.equals(aiAdviceConsent);
    profileService.updateAiAdviceConsent(user.getUserId(), consent);
    redirectAttributes.addFlashAttribute(
        "successMessage", consent ? "AI機能を有効にしました" : "AI機能を無効にしました");
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
      addGoalAttributes(model, user);
      return "user/profile";
    }
    profileService.updateProfile(principal.getName(), form);
    redirectAttributes.addFlashAttribute("successMessage", "プロフィールを更新しました");
    return "redirect:/user/profile";
  }
}
