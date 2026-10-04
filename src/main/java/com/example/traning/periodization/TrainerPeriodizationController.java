package com.example.traning.periodization;

import com.example.traning.periodization.dto.CreateCustomCycleRequest;
import com.example.traning.periodization.dto.SendProposalRequest;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * トレーナーが担当トレーニーに期分けプログラムの「案」を送る画面（モックアップ版11の07、詳細設計書3-5節）。 ORG_ADMIN/STORE_ADMIN専用（既存の
 * /trainer/advice と同じ）。
 */
@Controller
@RequestMapping("/trainer/periodization")
@PreAuthorize("hasAnyRole('ORG_ADMIN', 'STORE_ADMIN')")
@RequiredArgsConstructor
public class TrainerPeriodizationController {

  private final ProgramCycleProposalService proposalService;
  private final PeriodizationService periodizationService;
  private final UserService userService;

  private User trainer(Principal principal) {
    return userService.getUserByEmail(principal.getName());
  }

  /** 案の送信画面（宛先・現在のプログラム・プリセット一覧・これまでに送った案）。 */
  @GetMapping
  public String index(@RequestParam Long targetUserId, Model model, Principal principal) {
    User trainer = trainer(principal);
    // 担当チェックを兼ねる（担当外なら403）
    ProgramCycleProposalService.OutstandingProposals outstanding =
        proposalService.getOutstanding(trainer, targetUserId);
    User trainee =
        userService.findAll().stream()
            .filter(u -> u.getUserId().longValue() == targetUserId)
            .findFirst()
            .orElseThrow();
    model.addAttribute("trainee", trainee);
    PeriodizationViews.CycleDetail active =
        periodizationService
            .getActiveCycleDetail(targetUserId, java.time.LocalDate.now())
            .orElse(null);
    model.addAttribute("activeCycle", active);
    // 予約中の案が始まる日（今のサイクルの終了日の翌日）。送信前の確認表示に使う
    model.addAttribute(
        "scheduledStartDate",
        active != null ? active.startDate().plusDays(7L * active.totalWeeks()).toString() : null);
    model.addAttribute("presets", periodizationService.listPresets(targetUserId));
    model.addAttribute("pending", outstanding.pending());
    model.addAttribute("scheduled", outstanding.scheduled());
    model.addAttribute("sent", proposalService.getSentByTrainer(trainer, targetUserId));
    return "trainer/periodization";
  }

  /** 案を送る（送信前の確認は画面側で表示済み）。 */
  @PostMapping("/proposals")
  @ResponseBody
  public Map<String, Object> send(
      @Valid @RequestBody SendProposalRequest req, Principal principal) {
    Long id =
        proposalService.sendProposal(
            trainer(principal), req.traineeUserId(), req.presetProgramId());
    return Map.of("proposalId", id);
  }

  /** 案の編集画面（返事待ち・予約中のみ）。中身の取得・保存は画面のJSが行う。 */
  @GetMapping("/proposals/{proposalId}/edit")
  public String edit(@PathVariable Long proposalId, Model model, Principal principal) {
    PeriodizationViews.ProposalView view =
        proposalService.getViewForTrainer(trainer(principal), proposalId);
    model.addAttribute("proposal", view);
    return "trainer/periodization_edit";
  }

  @GetMapping("/proposals/{proposalId}/content")
  @ResponseBody
  public ResponseEntity<PeriodizationViews.CustomCycleInput> content(
      @PathVariable Long proposalId, Principal principal) {
    User trainer = trainer(principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(proposalService.getContent(trainer.getUserId().longValue(), proposalId));
  }

  /** 案の中身を更新する（返事待ち・予約中のみ、トレーニーの承認は不要）。 */
  @PostMapping("/proposals/{proposalId}/content")
  @ResponseBody
  public Map<String, Object> updateContent(
      @PathVariable Long proposalId,
      @Valid @RequestBody CreateCustomCycleRequest req,
      Principal principal) {
    proposalService.updateContent(
        trainer(principal), proposalId, PeriodizationApiController.toInput(req));
    List<Boolean> deload =
        req.weeks().stream()
            .sorted(java.util.Comparator.comparing(CreateCustomCycleRequest.Week::weekNumber))
            .map(w -> Boolean.TRUE.equals(w.deload()))
            .toList();
    return Map.of(
        "warnings",
        CustomCycleRules.evaluate(deload).stream().map(CustomCycleRules.Warning::message).toList());
  }

  /** 返事待ちの案を取り下げる。 */
  @PostMapping("/proposals/{proposalId}/withdraw")
  @ResponseBody
  public Map<String, Object> withdraw(@PathVariable Long proposalId, Principal principal) {
    proposalService.withdraw(trainer(principal), proposalId);
    return Map.of("result", "ok");
  }
}
