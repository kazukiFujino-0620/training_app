package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.goal.FrequencyGoalService;
import com.example.traning.mobile.dto.FrequencyGoalUpdateRequest;
import com.example.traning.mobile.dto.MobileFrequencyGoalResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイル: 週の回数目標と達成率（機能見直し-1-#7）。Web用API（/api/frequency-goal）とは別Controller・別DTO（CLAUDE.md方針）。
 * 対象はJWTのユーザー本人のデータのみ。
 */
@RestController
@RequestMapping("/api/mobile/frequency-goal")
@PreAuthorize("isAuthenticated()")
public class MobileFrequencyGoalController {

  private final FrequencyGoalService frequencyGoalService;

  public MobileFrequencyGoalController(FrequencyGoalService frequencyGoalService) {
    this.frequencyGoalService = frequencyGoalService;
  }

  /** カードと設定シートの表示データ（達成率は終わった週のみで計算し、今週は進捗だけ）。 */
  @GetMapping
  public ResponseEntity<MobileFrequencyGoalResponse> get(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        MobileFrequencyGoalResponse.from(frequencyGoalService.getSummary(userId, LocalDate.now())));
  }

  /** 手入力の目標を保存する（1〜7回）。来週の評価から反映し、保存後の表示データを返す。 */
  @PutMapping
  @AuditLog(action = "MOBILE_FREQUENCY_GOAL_UPDATE", targetTable = "user_frequency_goals")
  public ResponseEntity<MobileFrequencyGoalResponse> update(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody FrequencyGoalUpdateRequest req) {
    LocalDate today = LocalDate.now();
    frequencyGoalService.updateGoal(userId, req.getWeeklyTargetCount(), today);
    return ResponseEntity.ok(
        MobileFrequencyGoalResponse.from(frequencyGoalService.getSummary(userId, today)));
  }
}
