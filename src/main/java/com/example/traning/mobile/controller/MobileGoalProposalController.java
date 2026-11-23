package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.goal.GoalProposalService;
import com.example.traning.goal.GoalProposalService.AgreementView;
import com.example.traning.goal.GoalProposalService.MemberProposalView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * モバイル: トレーナーからの目標の提案（機能見直し-1-#20）。トップ（TrainingListScreen）の提案のカードと、プロフィールの
 * 「担当トレーナーと決めた目標」の表示で使う。対象はJWTのユーザー本人のデータのみ。
 */
@RestController
@RequestMapping("/api/mobile/goal-proposals")
@PreAuthorize("isAuthenticated()")
public class MobileGoalProposalController {

  private final GoalProposalService goalProposalService;

  public MobileGoalProposalController(GoalProposalService goalProposalService) {
    this.goalProposalService = goalProposalService;
  }

  /** 返事待ちの提案。 */
  public record PendingProposalResponse(
      Long id,
      String title,
      String proposerName,
      String currentWeeklyLabel,
      String proposedWeeklyLabel,
      boolean weeklyChanged,
      String currentGoalModeLabel,
      String proposedGoalModeLabel,
      boolean goalModeChanged,
      String comment,
      LocalDate nextReviewDate,
      String nextReviewLabel) {

    static PendingProposalResponse from(MemberProposalView v) {
      return new PendingProposalResponse(
          v.id(),
          v.title(),
          v.proposerName(),
          v.currentWeeklyLabel(),
          v.proposedWeeklyLabel(),
          v.weeklyChanged(),
          v.currentGoalModeLabel(),
          v.proposedGoalModeLabel(),
          v.goalModeChanged(),
          v.comment(),
          v.nextReviewDate(),
          v.nextReviewLabel());
    }
  }

  /** 返事待ちの提案の有無（無ければproposal=null）。 */
  public record PendingResponse(PendingProposalResponse proposal) {}

  /** 「担当トレーナーと決めた目標」の表示（今の目標が承認した提案と違えばagreed=false）。 */
  public record AgreementResponse(boolean agreed, String tagLabel, String nextReviewLabel) {
    static AgreementResponse from(AgreementView v) {
      return v == null
          ? new AgreementResponse(false, null, null)
          : new AgreementResponse(true, v.tagLabel(), v.nextReviewLabel());
    }
  }

  /** response: ACCEPT（この目標にする）／DECLINE（今回は見送る）。 */
  public record RespondRequest(String response) {}

  /** GoalProposalServiceが投げるResponseStatusException（400/404/409）をステータスどおりのJSONで返す。 */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
    String reason = ex.getReason() != null ? ex.getReason() : "リクエストを処理できませんでした";
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
  }

  @GetMapping("/pending")
  public ResponseEntity<PendingResponse> pending(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        new PendingResponse(
            goalProposalService
                .pendingForMember(userId)
                .map(PendingProposalResponse::from)
                .orElse(null)));
  }

  @GetMapping("/agreement")
  public ResponseEntity<AgreementResponse> agreement(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        AgreementResponse.from(goalProposalService.agreementFor(userId).orElse(null)));
  }

  @AuditLog(action = "MOBILE_GOAL_PROPOSAL_RESPOND", targetTable = "goal_proposals")
  @PostMapping("/{id}/respond")
  public ResponseEntity<Void> respond(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody RespondRequest req) {
    String response = req == null ? null : req.response();
    boolean accept;
    if ("ACCEPT".equals(response)) {
      accept = true;
    } else if ("DECLINE".equals(response)) {
      accept = false;
    } else {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "選択肢を指定してください");
    }
    goalProposalService.respond(userId, id, accept, LocalDate.now(), LocalDateTime.now());
    return ResponseEntity.noContent().build();
  }
}
