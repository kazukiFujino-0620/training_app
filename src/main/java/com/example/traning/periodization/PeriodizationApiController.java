package com.example.traning.periodization;

import com.example.traning.periodization.PeriodizationViews.CycleDetail;
import com.example.traning.periodization.PeriodizationViews.ItemInput;
import com.example.traning.periodization.PeriodizationViews.PresetSummary;
import com.example.traning.periodization.PeriodizationViews.TodayAssignment;
import com.example.traning.periodization.dto.AdoptPresetRequest;
import com.example.traning.periodization.dto.CreateCustomCycleRequest;
import com.example.traning.periodization.dto.CustomizeDayRequest;
import com.example.traning.periodization.dto.CustomizeItemsRequest;
import com.example.traning.periodization.dto.RenewCycleRequest;
import com.example.traning.training.service.TrainingService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 期分けプログラムのWeb用API（機能見直し-1-#3）。画面（program_cycle.html）はモックアップ確認後に追加する。
 * モバイルは別Controller・別DTO（MobilePeriodizationController）。
 */
@RestController
@RequestMapping("/api/periodization")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class PeriodizationApiController {

  private final PeriodizationService periodizationService;
  private final TrainingService trainingService;
  private final ProgramCycleProposalService proposalService;

  private Long userId(Principal principal) {
    return trainingService.getUserIdByEmail(principal.getName());
  }

  @GetMapping("/presets")
  public List<PresetSummary> presets(Principal principal) {
    return periodizationService.listPresets(userId(principal));
  }

  /** 採用中サイクルの全体像。無ければ204。 */
  @GetMapping("/cycle")
  public ResponseEntity<CycleDetail> activeCycle(Principal principal) {
    return periodizationService
        .getActiveCycleDetail(userId(principal), LocalDate.now())
        .map(d -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(d))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/today")
  public ResponseEntity<TodayAssignment> today(Principal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(periodizationService.getTodayAssignment(userId(principal), LocalDate.now()));
  }

  @PostMapping("/adopt")
  public Map<String, Object> adopt(
      @Valid @RequestBody AdoptPresetRequest req, Principal principal) {
    Long cycleId =
        periodizationService.adoptPreset(userId(principal), req.presetProgramId(), LocalDate.now());
    return Map.of("cycleId", cycleId);
  }

  /** 白紙作成画面用のルール（週数範囲・注意表示の閾値と文言）。 */
  @GetMapping("/custom/rules")
  public PeriodizationViews.CustomCycleRulesView customRules() {
    return PeriodizationViews.CustomCycleRulesView.current();
  }

  /** 白紙から組んで開始する（tier=INTERMEDIATE_CUSTOM）。warningsは保存を妨げない注意。 */
  @PostMapping("/custom")
  public Map<String, Object> createCustom(
      @Valid @RequestBody CreateCustomCycleRequest req, Principal principal) {
    PeriodizationViews.CustomCycleResult result =
        periodizationService.createCustomCycle(userId(principal), toInput(req), LocalDate.now());
    return Map.of(
        "cycleId",
        result.cycleId(),
        "warnings",
        result.warnings().stream().map(CustomCycleRules.Warning::message).toList());
  }

  static PeriodizationViews.CustomCycleInput toInput(CreateCustomCycleRequest req) {
    return new PeriodizationViews.CustomCycleInput(
        req.name(),
        req.totalWeeks(),
        req.weeks().stream()
            .map(
                w ->
                    new PeriodizationViews.WeekInput(
                        w.weekNumber(), w.targetIntensityPct(), w.deload()))
            .toList(),
        req.days() == null
            ? List.of()
            : req.days().stream()
                .map(
                    d ->
                        new PeriodizationViews.DayInput(
                            d.weekNumber(),
                            d.dayOfWeek(),
                            d.partCode(),
                            d.items() == null
                                ? List.of()
                                : d.items().stream()
                                    .map(i -> new ItemInput(i.itemName(), i.targetSets()))
                                    .toList()))
                .toList());
  }

  @PostMapping("/cycles/{cycleId}/day-templates")
  public ResponseEntity<Void> customizeDay(
      @PathVariable Long cycleId,
      @Valid @RequestBody CustomizeDayRequest req,
      Principal principal) {
    periodizationService.customizeDayTemplate(
        userId(principal), cycleId, req.weekNumber(), req.dayOfWeek(), req.partCode());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/day-templates/{dayTemplateId}/items")
  public ResponseEntity<Void> customizeItems(
      @PathVariable Long dayTemplateId,
      @Valid @RequestBody CustomizeItemsRequest req,
      Principal principal) {
    List<ItemInput> items =
        req.items().stream().map(i -> new ItemInput(i.itemName(), i.targetSets())).toList();
    periodizationService.customizeDayTemplateItems(userId(principal), dayTemplateId, items);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/renew")
  public Map<String, Object> renew(@Valid @RequestBody RenewCycleRequest req, Principal principal) {
    Optional<Long> cycleId =
        periodizationService.renewCycle(userId(principal), req.choice(), req.presetProgramId());
    Map<String, Object> body = new HashMap<>();
    body.put("cycleId", cycleId.orElse(null));
    return body;
  }

  /** トレーナーからの案（返事待ち・予約中）。/menuのバナーとprogram_cycle.htmlの案カードで使う。 */
  @GetMapping("/proposals")
  public ResponseEntity<PeriodizationViews.TraineeProposals> proposals(Principal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(proposalService.getTraineeProposals(userId(principal)));
  }

  /** 案の中身（週ごとの強度・曜日ごとの部位と種目）。 */
  @GetMapping("/proposals/{proposalId}/content")
  public PeriodizationViews.CustomCycleInput proposalContent(
      @PathVariable Long proposalId, Principal principal) {
    return proposalService.getContent(userId(principal), proposalId);
  }

  /** 案に対する選択（START_NOW / SCHEDULE / DECLINE）。 */
  @PostMapping("/proposals/{proposalId}/respond")
  public Map<String, Object> respond(
      @PathVariable Long proposalId,
      @Valid @RequestBody com.example.traning.periodization.dto.RespondProposalRequest req,
      Principal principal) {
    Long cycleId = proposalService.respond(userId(principal), proposalId, req.response());
    Map<String, Object> body = new HashMap<>();
    body.put("cycleId", cycleId);
    return body;
  }
}
