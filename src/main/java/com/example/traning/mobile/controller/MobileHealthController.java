package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.health.HealthInputRejectedException;
import com.example.traning.health.HealthRestingHeartRateService;
import com.example.traning.health.HealthSleepService;
import com.example.traning.health.HealthSyncService;
import com.example.traning.mobile.dto.HealthSummaryResponse;
import com.example.traning.mobile.dto.HealthSyncRequest;
import com.example.traning.mobile.dto.ManualRestingHeartRateRequest;
import com.example.traning.mobile.dto.ManualSleepRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthKit/Health Connect連携の同期API（ita3-1）。
 *
 * <p>読み取り専用連携のため、モバイル端末側でOSのヘルスケアAPIから取得した値をこのエンドポイントへPUSHするのみで、 サーバー側からヘルスケアデータを書き戻す処理は無い。
 */
@RestController
@RequestMapping("/api/mobile/health")
@PreAuthorize("isAuthenticated()")
public class MobileHealthController {

  private final HealthSyncService healthSyncService;
  private final HealthSleepService healthSleepService;
  private final HealthRestingHeartRateService healthRestingHeartRateService;

  public MobileHealthController(
      HealthSyncService healthSyncService,
      HealthSleepService healthSleepService,
      HealthRestingHeartRateService healthRestingHeartRateService) {
    this.healthSyncService = healthSyncService;
    this.healthSleepService = healthSleepService;
    this.healthRestingHeartRateService = healthRestingHeartRateService;
  }

  @PostMapping("/sync")
  @AuditLog(action = "MOBILE_HEALTH_SYNC", targetTable = "health_steps")
  public ResponseEntity<Map<String, Object>> sync(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody HealthSyncRequest req) {
    int syncedCount = healthSyncService.sync(userId, req);
    return ResponseEntity.ok(Map.of("syncedCount", syncedCount));
  }

  /** 各指標の最新値をまとめて返す（表示画面用）。 */
  @GetMapping("/summary")
  public ResponseEntity<HealthSummaryResponse> summary(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(healthSyncService.getSummary(userId));
  }

  /** 機能見直し-1-#5: 睡眠時間の手動入力（時間のみ）。同日の記録があれば同期元を問わず上書きする。 */
  @PutMapping("/sleep")
  @AuditLog(action = "MOBILE_HEALTH_SLEEP_MANUAL", targetTable = "health_sleep")
  public ResponseEntity<Void> saveManualSleep(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ManualSleepRequest req) {
    if (req.getDate().isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("日付は今日以前を指定してください");
    }
    healthSleepService.saveManual(
        userId, req.getDate(), req.getDurationMinutes(), req.isConfirmed());
    return ResponseEntity.noContent().build();
  }

  /** 機能見直し-1-#5: 安静時心拍の手動入力。同日の記録があれば同期元を問わず上書きする。 */
  @PutMapping("/resting-heart-rate")
  @AuditLog(action = "MOBILE_HEALTH_RESTING_HR_MANUAL", targetTable = "health_resting_heart_rate")
  public ResponseEntity<Void> saveManualRestingHeartRate(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ManualRestingHeartRateRequest req) {
    if (req.getDate().isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("日付は今日以前を指定してください");
    }
    healthRestingHeartRateService.saveManual(
        userId, req.getDate(), req.getRestingBpm(), req.isConfirmed());
    return ResponseEntity.noContent().build();
  }

  /**
   * 手動入力を保存しなかった場合の応答（機能見直し-1-#5）。入力エラーは400、確認範囲で未確認は409＋code=CONFIRMATION_REQUIRED
   * （アプリは確認表示を出し、利用者が保存を選んだらconfirmed=trueで再送する）。
   */
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
