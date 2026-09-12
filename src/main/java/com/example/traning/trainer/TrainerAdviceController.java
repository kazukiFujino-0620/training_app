package com.example.traning.trainer;

import com.example.traning.audit.AuditLog;
import com.example.traning.common.WebErrorCode;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** トレーナーアドバイス送信画面（ita4-4 (A)）。ORG_ADMIN/STORE_ADMIN専用。 */
@Controller
@RequestMapping("/trainer/advice")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'STORE_ADMIN')")
public class TrainerAdviceController {

  private final TrainerAdviceService trainerAdviceService;
  private final TrainerAdviceDraftService trainerAdviceDraftService;
  private final UserService userService;

  public TrainerAdviceController(
      TrainerAdviceService trainerAdviceService,
      TrainerAdviceDraftService trainerAdviceDraftService,
      UserService userService) {
    this.trainerAdviceService = trainerAdviceService;
    this.trainerAdviceDraftService = trainerAdviceDraftService;
    this.userService = userService;
  }

  private User currentUser(Principal principal) {
    return userService.getUserByEmail(principal.getName());
  }

  /**
   * @param targetUserId トレーニング詳細画面から遷移した場合の宛先の事前選択（任意）
   * @param date トレーニング詳細画面から遷移した場合の対象日の事前選択（任意）
   */
  @GetMapping
  public String index(
      @RequestParam(required = false) Long targetUserId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      Model model,
      Principal principal) {
    User trainer = currentUser(principal);
    model.addAttribute("trainees", trainerAdviceService.listTrainees(trainer));
    model.addAttribute("sentAdvices", trainerAdviceService.listSentByTrainer(trainer));
    model.addAttribute("selectedTargetUserId", targetUserId);
    model.addAttribute("selectedDate", date != null ? date : LocalDate.now());
    model.addAttribute(
        "assignmentTrainees", trainerAdviceService.listTraineesForAssignmentManagement(trainer));
    model.addAttribute("eligibleTrainers", trainerAdviceService.listEligibleTrainers(trainer));
    model.addAttribute("trainerNames", allTrainerNamesById());
    return "trainer/advice";
  }

  /** 担当トレーナー変更UIでの表示専用。スコープ外のトレーナーが担当のケースも名前を出せるよう、全トレーナーを対象にする。 */
  private Map<Long, String> allTrainerNamesById() {
    return userService.findAll().stream()
        .filter(
            u ->
                Role.ORG_ADMIN.value().equals(u.getRole())
                    || Role.STORE_ADMIN.value().equals(u.getRole()))
        .collect(Collectors.toMap(u -> u.getUserId().longValue(), User::getUserName));
  }

  /** 担当トレーナーの変更（ita4結合試験バグ6追加対応）。{@code newTrainerId}が空の場合は未割り当てに戻す。 */
  @AuditLog(action = "TRAINER_REASSIGN", targetTable = "users")
  @PostMapping("/assignments/{traineeId}")
  public String reassign(
      @PathVariable Long traineeId,
      @RequestParam(required = false) Long newTrainerId,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User trainer = currentUser(principal);
    try {
      trainerAdviceService.reassignTrainer(trainer, traineeId, newTrainerId);
      redirectAttributes.addFlashAttribute("successMessage", "担当トレーナーを変更しました。");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
    }
    return "redirect:/trainer/advice";
  }

  @AuditLog(action = "TRAINER_ADVICE_SEND", targetTable = "trainer_advices")
  @PostMapping
  public String send(
      @RequestParam Long targetUserId,
      @RequestParam String body,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User trainer = currentUser(principal);
    try {
      trainerAdviceService.send(trainer, targetUserId, body, targetDate);
      redirectAttributes.addFlashAttribute("successMessage", "メッセージを送信しました。");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
    }
    return "redirect:/trainer/advice";
  }

  /** ita5-1 機能2: トレーナーアドバイスのAI下書き生成。「AIで下書き」ボタン押下時のみ呼ばれる（自動生成はしない）。 */
  @PostMapping("/draft")
  @ResponseBody
  public ResponseEntity<Map<String, String>> draft(
      @RequestParam Long targetUserId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
      Principal principal) {
    User trainer = currentUser(principal);
    try {
      Optional<String> generated =
          trainerAdviceDraftService.generateDraft(trainer, targetUserId, targetDate);
      if (generated.isEmpty()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", "AI機能を利用するには設定画面（プロフィール）で同意が必要です"));
      }
      return ResponseEntity.ok(Map.of("draft", generated.get()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @AuditLog(action = "TRAINER_ADVICE_DELETE", targetTable = "trainer_advices")
  @PostMapping("/{id}/delete")
  public String delete(
      @PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
    User trainer = currentUser(principal);
    trainerAdviceService.delete(trainer, id);
    redirectAttributes.addFlashAttribute("successMessage", "メッセージを取り下げました。");
    return "redirect:/trainer/advice";
  }
}
