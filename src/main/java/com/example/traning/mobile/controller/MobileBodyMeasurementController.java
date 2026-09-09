package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.mobile.dto.MobileBodyMeasurementResponse;
import com.example.traning.mobile.dto.SaveBodyMeasurementRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイル: 体重・体脂肪率の手動記録API（ita7-1）。
 *
 * <p>既存の{@link BodyMeasurementService}をそのまま流用する。HealthKit自動同期との同日競合は要件定義0-4/1-2で確定の通り、
 * 優先度づけ・警告表示なしの単純upsertで両経路とも統一済みのため、ここでの追加対応は不要。
 */
@RestController
@RequestMapping("/api/mobile/body")
@PreAuthorize("isAuthenticated()")
public class MobileBodyMeasurementController {

  private final BodyMeasurementService bodyMeasurementService;

  public MobileBodyMeasurementController(BodyMeasurementService bodyMeasurementService) {
    this.bodyMeasurementService = bodyMeasurementService;
  }

  @GetMapping
  public ResponseEntity<List<MobileBodyMeasurementResponse>> getAll(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        bodyMeasurementService.getAll(userId).stream()
            .map(MobileBodyMeasurementResponse::from)
            .toList());
  }

  @PostMapping
  @Transactional
  @AuditLog(action = "MOBILE_BODY_MEASUREMENT_SAVE", targetTable = "body_measurements")
  public ResponseEntity<Void> save(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody SaveBodyMeasurementRequest req) {
    if (req.getMeasuredDate().isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("日付は今日以前を指定してください");
    }
    bodyMeasurementService.save(
        userId, req.getMeasuredDate(), req.getWeightKg(), req.getBodyFatPct(), req.getMemo());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  @Transactional
  @AuditLog(action = "MOBILE_BODY_MEASUREMENT_DELETE", targetTable = "body_measurements")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    bodyMeasurementService.delete(id, userId);
    return ResponseEntity.noContent().build();
  }
}
