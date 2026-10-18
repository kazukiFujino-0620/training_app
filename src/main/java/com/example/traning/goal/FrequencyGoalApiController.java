package com.example.traning.goal;

import com.example.traning.audit.AuditLog;
import com.example.traning.training.service.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web版: 週の回数目標の取得・保存API（機能見直し-1-#7）。メニュー画面の「週の目標」カード・設定ダイアログから使う。
 *
 * <p>モバイル（/api/mobile/frequency-goal）とは別Controller・別DTO。Service層は共通。対象はログイン中のユーザー本人のデータのみ。
 */
@RestController
@RequestMapping("/api/frequency-goal")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class FrequencyGoalApiController {

  private final FrequencyGoalService frequencyGoalService;
  private final TrainingService trainingService;

  @Data
  public static class GoalForm {
    @NotNull(message = "週の回数目標は必須です")
    @Min(value = FrequencyGoalService.MIN_TARGET, message = "週の回数目標は1〜7回の範囲で指定してください")
    @Max(value = FrequencyGoalService.MAX_TARGET, message = "週の回数目標は1〜7回の範囲で指定してください")
    private Integer weeklyTargetCount;
  }

  /** Web用の表示データ（文言はService層の表示データから組み立て済み）。 */
  public record FrequencyGoalWebResponse(
      boolean goalSet,
      String goalLabel,
      String thisWeekLabel,
      String resultLabel,
      String percentLabel,
      List<String> notes,
      boolean thisWeekFromProgram,
      Integer manualTarget,
      boolean programActive,
      String programSheetLabel) {

    static FrequencyGoalWebResponse from(FrequencyGoalSummary s) {
      return new FrequencyGoalWebResponse(
          s.goalSet(),
          s.goalLabel(),
          s.thisWeekLabel(),
          s.resultLabel(),
          s.percentLabel(),
          s.notes(),
          s.thisWeekFromProgram(),
          s.manualTarget(),
          s.programActive(),
          s.programSheetLabel());
    }
  }

  @GetMapping
  public ResponseEntity<FrequencyGoalWebResponse> get(Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    return ResponseEntity.ok(
        FrequencyGoalWebResponse.from(frequencyGoalService.getSummary(userId, LocalDate.now())));
  }

  /** 保存した目標は来週の評価から反映する（今週は変更前の目標で判定）。プログラム実施中でも保存でき、プログラム終了後の週から使われる。 */
  @PutMapping
  @AuditLog(action = "FREQUENCY_GOAL_UPDATE", targetTable = "user_frequency_goals")
  public ResponseEntity<?> update(
      @Valid @RequestBody GoalForm form, BindingResult result, Principal principal) {
    if (result.hasErrors()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", result.getAllErrors().get(0).getDefaultMessage()));
    }
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    LocalDate today = LocalDate.now();
    frequencyGoalService.updateGoal(userId, form.getWeeklyTargetCount(), today);
    return ResponseEntity.ok(
        FrequencyGoalWebResponse.from(frequencyGoalService.getSummary(userId, today)));
  }
}
