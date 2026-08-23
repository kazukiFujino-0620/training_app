package com.example.traning.trainer;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** トレーナーアドバイス送信画面（ita4-4 (A)）。TRAINERロール専用（STORE_ADMIN等はこの画面にアクセスできない）。 */
@Controller
@RequestMapping("/trainer/advice")
@PreAuthorize("hasRole('TRAINER')")
public class TrainerAdviceController {

  private final TrainerAdviceService trainerAdviceService;
  private final UserService userService;

  public TrainerAdviceController(
      TrainerAdviceService trainerAdviceService, UserService userService) {
    this.trainerAdviceService = trainerAdviceService;
    this.userService = userService;
  }

  private User currentUser(Principal principal) {
    return userService.getUserByEmail(principal.getName());
  }

  @GetMapping
  public String index(Model model, Principal principal) {
    User trainer = currentUser(principal);
    model.addAttribute("trainees", trainerAdviceService.listTrainees(trainer));
    model.addAttribute("sentAdvices", trainerAdviceService.listSentByTrainer(trainer));
    return "trainer/advice";
  }

  @AuditLog(action = "TRAINER_ADVICE_SEND", targetTable = "trainer_advices")
  @PostMapping
  public String send(
      @RequestParam Long targetUserId,
      @RequestParam String body,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User trainer = currentUser(principal);
    try {
      trainerAdviceService.send(trainer, targetUserId, body);
      redirectAttributes.addFlashAttribute("successMessage", "メッセージを送信しました。");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/trainer/advice";
  }

  @AuditLog(action = "TRAINER_ADVICE_DELETE", targetTable = "trainer_advices")
  @PostMapping("/{id}/delete")
  public String delete(
      @PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
    User trainer = currentUser(principal);
    try {
      trainerAdviceService.delete(trainer, id);
      redirectAttributes.addFlashAttribute("successMessage", "メッセージを取り下げました。");
    } catch (Exception e) {
      redirectAttributes.addFlashAttribute("errorMessage", "取り下げに失敗しました。");
    }
    return "redirect:/trainer/advice";
  }
}
