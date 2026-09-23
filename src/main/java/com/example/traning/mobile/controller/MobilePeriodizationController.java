package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.AdoptRequest;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CustomCycleRequest;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CustomCycleResponse;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CustomCycleRulesResponse;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CustomizeItemsRequest;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CycleIdResponse;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.CycleResponse;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.PresetResponse;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.RenewRequest;
import com.example.traning.mobile.dto.MobilePeriodizationDtos.TodayResponse;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.periodization.PeriodizationViews.ItemInput;
import com.example.traning.periodization.RenewChoice;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 期分けプログラム（モバイル向けREST API、機能見直し-1-#3 詳細設計書5-1節）。 */
@RestController
@RequestMapping("/api/mobile/periodization")
public class MobilePeriodizationController {

  private final PeriodizationService periodizationService;

  public MobilePeriodizationController(PeriodizationService periodizationService) {
    this.periodizationService = periodizationService;
  }

  /**
   * PeriodizationServiceが投げるResponseStatusException（400/403/404/409）をステータスどおりのJSONで返す。
   * MobileExceptionHandlerの汎用ハンドラーでは500に丸められるため、このController内に限定して処理する。
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
    String reason = ex.getReason() != null ? ex.getReason() : "リクエストを処理できませんでした";
    return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", reason));
  }

  @GetMapping("/presets")
  public ResponseEntity<List<PresetResponse>> presets(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        periodizationService.listPresets(userId).stream().map(PresetResponse::from).toList());
  }

  /** 採用中サイクルの全体像（週送りタブ表示用）。無ければ204。 */
  @GetMapping("/cycle")
  public ResponseEntity<CycleResponse> cycle(@AuthenticationPrincipal Long userId) {
    return periodizationService
        .getActiveCycleDetail(userId, LocalDate.now())
        .map(c -> ResponseEntity.ok(CycleResponse.from(c)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/today")
  public ResponseEntity<TodayResponse> today(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        TodayResponse.from(periodizationService.getTodayAssignment(userId, LocalDate.now())));
  }

  @AuditLog(action = "MOBILE_PERIODIZATION_ADOPT", targetTable = "program_cycles")
  @PostMapping("/adopt")
  public ResponseEntity<CycleIdResponse> adopt(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AdoptRequest req) {
    Long cycleId = periodizationService.adoptPreset(userId, req.presetProgramId(), LocalDate.now());
    return ResponseEntity.ok(new CycleIdResponse(cycleId));
  }

  /** 白紙作成画面用のルール（週数範囲・注意表示の閾値と文言）。 */
  @GetMapping("/custom/rules")
  public ResponseEntity<CustomCycleRulesResponse> customRules() {
    return ResponseEntity.ok(CustomCycleRulesResponse.current());
  }

  @AuditLog(action = "MOBILE_PERIODIZATION_CREATE_CUSTOM", targetTable = "program_cycles")
  @PostMapping("/custom")
  public ResponseEntity<CustomCycleResponse> createCustom(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CustomCycleRequest req) {
    return ResponseEntity.ok(
        CustomCycleResponse.from(
            periodizationService.createCustomCycle(userId, req.toInput(), LocalDate.now())));
  }

  @AuditLog(
      action = "MOBILE_PERIODIZATION_CUSTOMIZE_ITEMS",
      targetTable = "program_cycle_day_template_items")
  @PostMapping("/day-templates/{id}/items")
  public ResponseEntity<Void> customizeItems(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody CustomizeItemsRequest req) {
    List<ItemInput> items =
        req.items().stream().map(i -> new ItemInput(i.itemName(), i.targetSets())).toList();
    periodizationService.customizeDayTemplateItems(userId, id, items);
    return ResponseEntity.noContent().build();
  }

  @AuditLog(action = "MOBILE_PERIODIZATION_RENEW", targetTable = "program_cycles")
  @PostMapping("/renew")
  public ResponseEntity<CycleIdResponse> renew(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody RenewRequest req) {
    // QA Q3-7追記（2026-09-23 USER確定）: モバイルには週間プログラム画面が無いため、
    // 期分けをやめる操作（GO_FREEFORM）はWeb版のみで提供する。モバイルの3つ目の選択肢は「自分で組む」（POST /custom）。
    if (req.choice() == RenewChoice.GO_FREEFORM) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "モバイルからは期分けの終了（通常の週間プログラムへの切り替え）はできません");
    }
    Long cycleId =
        periodizationService.renewCycle(userId, req.choice(), req.presetProgramId()).orElse(null);
    return ResponseEntity.ok(new CycleIdResponse(cycleId));
  }
}
