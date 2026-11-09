package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.MobileWorkoutGenerationDtos.AddExclusionRequest;
import com.example.traning.mobile.dto.MobileWorkoutGenerationDtos.GenerateRequest;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationService;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.ExclusionView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.FormView;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerateCommand;
import com.example.traning.smarttrainer.autogen.WorkoutGenerationViews.GenerationResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#4 メニュー自動作成（モバイル向けREST API）。対象は常にログイン中のユーザー自身のデータのみ。 */
@RestController
@RequestMapping("/api/mobile/workout-generation")
public class MobileWorkoutGenerationController {

  private final WorkoutGenerationService workoutGenerationService;

  public MobileWorkoutGenerationController(WorkoutGenerationService workoutGenerationService) {
    this.workoutGenerationService = workoutGenerationService;
  }

  /** WorkoutGenerationServiceが投げるResponseStatusException（400/404/409）をステータスどおりのJSONで返す。 */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
    String reason = ex.getReason() != null ? ex.getReason() : "リクエストを処理できませんでした";
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "リクエストの形式が正しくありません"));
  }

  /** 条件入力画面の表示に必要な情報（部位・器具・除外・対象日のプログラムの枠）。 */
  @GetMapping("/form")
  public ResponseEntity<FormView> form(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(workoutGenerationService.getForm(userId, date));
  }

  @PostMapping("/generate")
  public ResponseEntity<GenerationResult> generate(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody GenerateRequest req) {
    return ResponseEntity.ok(
        workoutGenerationService.generate(
            userId,
            new GenerateCommand(
                req.getAvailableMinutes(),
                req.getDate(),
                req.getDesiredPartCodes(),
                req.getAvailableEquipmentIds(),
                req.getExcludeItemNames())));
  }

  @GetMapping("/exclusions")
  public ResponseEntity<List<ExclusionView>> exclusions(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(workoutGenerationService.listExclusions(userId));
  }

  @AuditLog(action = "MOBILE_EXCLUDED_ITEM_ADD", targetTable = "user_excluded_items")
  @PostMapping("/exclusions")
  public ResponseEntity<ExclusionView> addExclusion(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AddExclusionRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(workoutGenerationService.addExclusion(userId, req.getItemName(), req.getReason()));
  }

  @AuditLog(action = "MOBILE_EXCLUDED_ITEM_DELETE", targetTable = "user_excluded_items")
  @DeleteMapping("/exclusions/{itemName}")
  public ResponseEntity<Void> removeExclusion(
      @AuthenticationPrincipal Long userId, @PathVariable String itemName) {
    workoutGenerationService.removeExclusion(userId, itemName);
    return ResponseEntity.noContent().build();
  }

  /** 除外の再確認の回答。keep=true「まだ必要」（次は4週間後）、keep=false「解除する」（削除）。 */
  @AuditLog(action = "MOBILE_EXCLUDED_ITEM_CONFIRM", targetTable = "user_excluded_items")
  @PutMapping("/exclusions/{itemName}/confirm")
  public ResponseEntity<Void> confirmExclusion(
      @AuthenticationPrincipal Long userId,
      @PathVariable String itemName,
      @RequestParam boolean keep) {
    workoutGenerationService.confirmExclusion(userId, itemName, keep);
    return ResponseEntity.noContent().build();
  }
}
