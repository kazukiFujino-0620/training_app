package com.example.traning.health;

import com.example.traning.audit.AuditLog;
import com.example.traning.training.service.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.security.Principal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web版: 睡眠・安静時心拍の手動入力API（機能見直し-1-#5、QA Q5-4）。
 *
 * <p>モバイル（/api/mobile/health/**）とは別Controller・別DTO。Service層は共通。
 */
@RestController
@RequestMapping("/api/health")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class HealthManualInputController {

  private final HealthSleepService healthSleepService;
  private final HealthRestingHeartRateService healthRestingHeartRateService;
  private final TrainingService trainingService;

  /** 前夜の主な睡眠（昼寝は含めない）。画面の「時間・分」はブラウザ側で合計分に換算して送る。0（徹夜）も可。 */
  @Data
  public static class SleepForm {
    @NotNull(message = "日付は必須です")
    private LocalDate date;

    @NotNull(message = "睡眠時間は必須です")
    private Integer durationMinutes;

    /** 確認範囲の値を、利用者が確認表示を見たうえで保存する場合にtrue。 */
    private boolean confirmed;
  }

  @Data
  public static class RestingHeartRateForm {
    @NotNull(message = "日付は必須です")
    private LocalDate date;

    @NotNull(message = "安静時心拍は必須です")
    private Integer restingBpm;

    /** 確認範囲の値を、利用者が確認表示を見たうえで保存する場合にtrue。 */
    private boolean confirmed;
  }

  @PutMapping("/sleep")
  @AuditLog(action = "HEALTH_SLEEP_MANUAL", targetTable = "health_sleep")
  public ResponseEntity<Map<String, String>> saveSleep(
      @Valid @RequestBody SleepForm form, BindingResult result, Principal principal) {
    if (result.hasErrors()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", result.getAllErrors().get(0).getDefaultMessage()));
    }
    if (form.getDate().isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "日付は今日以前を指定してください"));
    }
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    healthSleepService.saveManual(
        userId, form.getDate(), form.getDurationMinutes(), form.isConfirmed());
    return ResponseEntity.ok(Map.of("message", "保存しました"));
  }

  @PutMapping("/resting-heart-rate")
  @AuditLog(action = "HEALTH_RESTING_HR_MANUAL", targetTable = "health_resting_heart_rate")
  public ResponseEntity<Map<String, String>> saveRestingHeartRate(
      @Valid @RequestBody RestingHeartRateForm form, BindingResult result, Principal principal) {
    if (result.hasErrors()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", result.getAllErrors().get(0).getDefaultMessage()));
    }
    if (form.getDate().isAfter(LocalDate.now())) {
      return ResponseEntity.badRequest().body(Map.of("error", "日付は今日以前を指定してください"));
    }
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    healthRestingHeartRateService.saveManual(
        userId, form.getDate(), form.getRestingBpm(), form.isConfirmed());
    return ResponseEntity.ok(Map.of("message", "保存しました"));
  }

  /** 入力エラーは400、確認範囲で未確認は409＋code=CONFIRMATION_REQUIRED（画面は確認表示を出し、confirmed=trueで再送する）。 */
  @ExceptionHandler(HealthInputRejectedException.class)
  public ResponseEntity<Map<String, String>> handleRejected(HealthInputRejectedException ex) {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("error", ex.getMessage());
    if (ex.isConfirmationRequired()) {
      body.put("code", HealthInputRejectedException.CODE_CONFIRMATION_REQUIRED);
      return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
    return ResponseEntity.badRequest().body(body);
  }
}
