package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.health.HealthSyncService;
import com.example.traning.mobile.dto.HealthSyncRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
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

  public MobileHealthController(HealthSyncService healthSyncService) {
    this.healthSyncService = healthSyncService;
  }

  @PostMapping("/sync")
  @AuditLog(action = "MOBILE_HEALTH_SYNC", targetTable = "health_steps")
  public ResponseEntity<Map<String, Object>> sync(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody HealthSyncRequest req) {
    int syncedCount = healthSyncService.sync(userId, req);
    return ResponseEntity.ok(Map.of("syncedCount", syncedCount));
  }
}
