package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.RestPreferenceResponse;
import com.example.traning.mobile.dto.RestPreferenceUpsertRequest;
import com.example.traning.restpreference.RestPreferenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 17番: 種目別レスト時間の登録・編集（モバイル向けREST API）。 */
@RestController
@RequestMapping("/api/mobile/rest-preferences")
public class MobileRestPreferenceController {

  private final RestPreferenceService restPreferenceService;

  public MobileRestPreferenceController(RestPreferenceService restPreferenceService) {
    this.restPreferenceService = restPreferenceService;
  }

  @GetMapping
  public ResponseEntity<List<RestPreferenceResponse>> list(@AuthenticationPrincipal Long userId) {
    List<RestPreferenceResponse> body =
        restPreferenceService.listByUserId(userId).stream()
            .map(RestPreferenceResponse::from)
            .toList();
    return ResponseEntity.ok(body);
  }

  @AuditLog(action = "MOBILE_REST_PREFERENCE_UPSERT", targetTable = "user_item_rest_preferences")
  @PutMapping("/{itemName}")
  public ResponseEntity<RestPreferenceResponse> upsert(
      @AuthenticationPrincipal Long userId,
      @PathVariable String itemName,
      @Valid @RequestBody RestPreferenceUpsertRequest req) {
    var saved = restPreferenceService.upsert(userId, itemName, req.getRestSeconds());
    return ResponseEntity.ok(RestPreferenceResponse.from(saved));
  }

  @AuditLog(action = "MOBILE_REST_PREFERENCE_DELETE", targetTable = "user_item_rest_preferences")
  @DeleteMapping("/{itemName}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Long userId, @PathVariable String itemName) {
    restPreferenceService.delete(userId, itemName);
    return ResponseEntity.noContent().build();
  }
}
