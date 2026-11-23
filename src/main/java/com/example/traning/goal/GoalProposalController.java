package com.example.traning.goal;

import com.example.traning.audit.AuditLog;
import com.example.traning.common.WebErrorCode;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 目標をトレーナーと一緒に決める（機能見直し-1-#20）のWeb画面の受け付け。
 *
 * <ul>
 *   <li>スタッフ: 会員詳細（/admin/user/training-detail/{id}）の「目標の提案」から送る。提案できるかは {@link
 *       GoalProposalService}で判定する（担当外・範囲外は403）
 *   <li>会員: トップ（/menu）の提案のカードから「この目標にする」「今回は見送る」
 * </ul>
 */
@Controller
public class GoalProposalController {

  private final GoalProposalService goalProposalService;
  private final UserService userService;

  public GoalProposalController(GoalProposalService goalProposalService, UserService userService) {
    this.goalProposalService = goalProposalService;
    this.userService = userService;
  }

  @AuditLog(action = "GOAL_PROPOSAL_SEND", targetTable = "goal_proposals")
  @PostMapping("/trainer/members/{memberId}/goal-proposals")
  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN', 'TRAINER')")
  public String propose(
      @PathVariable Long memberId,
      @RequestParam(required = false) Integer weeklyTargetCount,
      @RequestParam(required = false) String goalMode,
      @RequestParam(required = false) String comment,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate nextReviewDate,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User viewer = userService.getUserByEmail(principal.getName());
    try {
      goalProposalService.propose(
          viewer,
          memberId,
          weeklyTargetCount,
          goalMode,
          comment,
          nextReviewDate,
          LocalDate.now(),
          LocalDateTime.now());
      redirectAttributes.addFlashAttribute("goalSuccessMessage", "目標の提案を送りました。");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("goalErrorMessage", e.getMessage());
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
    }
    return "redirect:/admin/user/training-detail/" + memberId + "#goal-card";
  }

  @AuditLog(action = "GOAL_PROPOSAL_RESPOND", targetTable = "goal_proposals")
  @PostMapping("/user/goal-proposals/{proposalId}/respond")
  @PreAuthorize("isAuthenticated()")
  public String respond(
      @PathVariable Long proposalId,
      @RequestParam String response,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User member = userService.getUserByEmail(principal.getName());
    boolean accept = parseResponse(response);
    try {
      goalProposalService.respond(
          member.getUserId().longValue(), proposalId, accept, LocalDate.now(), LocalDateTime.now());
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() != HttpStatus.CONFLICT) throw e;
      redirectAttributes.addFlashAttribute("error", e.getReason());
      return "redirect:/menu";
    }
    redirectAttributes.addFlashAttribute(
        "message", accept ? "目標を決めました。今週は目標を設定した週として、達成率には含めません。" : "今回の提案は見送りました。");
    return "redirect:/menu";
  }

  /** ACCEPT（この目標にする）／DECLINE（今回は見送る）。それ以外は400。 */
  static boolean parseResponse(String response) {
    if ("ACCEPT".equals(response)) return true;
    if ("DECLINE".equals(response)) return false;
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "選択肢を指定してください");
  }
}
