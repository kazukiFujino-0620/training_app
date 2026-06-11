package com.example.traning.template;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TemplateController {

  private final TrainingTemplateDao trainingTemplateDao;
  private final TrainingTemplateItemDao trainingTemplateItemDao;
  private final TrainingService trainingService;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final TrainingMasterDao trainingMasterDao;

  @GetMapping("/training/template")
  public String templatePage(Model model, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<TrainingTemplate> templates = trainingTemplateDao.selectByUserId(userId);
    for (TrainingTemplate t : templates) {
      t.setItems(trainingTemplateItemDao.selectByTemplateId(t.getId()));
    }
    model.addAttribute("templates", templates);

    List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
    java.util.Map<String, List<TrainingItemMaster>> itemsByPart = new java.util.LinkedHashMap<>();
    for (TrainingMaster part : parts) {
      itemsByPart.put(part.getPartCode(), trainingMasterDao.selectItemsByPart(part.getPartCode()));
    }
    model.addAttribute("parts", parts);
    model.addAttribute("itemsByPart", itemsByPart);

    return "training/template";
  }

  @GetMapping("/api/templates")
  @ResponseBody
  public ResponseEntity<List<TrainingTemplate>> listTemplates(Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<TrainingTemplate> templates = trainingTemplateDao.selectByUserId(userId);
    for (TrainingTemplate t : templates) {
      t.setItems(trainingTemplateItemDao.selectByTemplateId(t.getId()));
    }
    return ResponseEntity.ok(templates);
  }

  @AuditLog(action = "TEMPLATE_CREATE", targetTable = "training_templates")
  @PostMapping("/api/templates")
  @ResponseBody
  @Transactional
  public ResponseEntity<TrainingTemplate> createTemplate(
      @RequestBody TemplateRequest request, Principal principal) {
    if (request.getName() == null || request.getName().isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    TrainingTemplate template = new TrainingTemplate();
    template.setUserId(userId);
    template.setName(request.getName().trim());
    template.setPartCode(request.getPartCode() != null ? request.getPartCode() : "");
    template.setMemo(request.getMemo());
    trainingTemplateDao.insert(template);

    saveItems(template.getId(), request.getItems());
    template.setItems(trainingTemplateItemDao.selectByTemplateId(template.getId()));
    return ResponseEntity.ok(template);
  }

  @AuditLog(action = "TEMPLATE_UPDATE", targetTable = "training_templates")
  @PostMapping("/api/templates/{id}/update")
  @ResponseBody
  @Transactional
  public ResponseEntity<TrainingTemplate> updateTemplate(
      @PathVariable Long id, @RequestBody TemplateRequest request, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    Optional<TrainingTemplate> opt = trainingTemplateDao.selectById(id);
    if (opt.isEmpty()) return ResponseEntity.notFound().build();
    TrainingTemplate template = opt.get();
    if (!template.getUserId().equals(userId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    template.setName(request.getName() != null ? request.getName().trim() : template.getName());
    template.setPartCode(
        request.getPartCode() != null ? request.getPartCode() : template.getPartCode());
    template.setMemo(request.getMemo());
    template.setUpdatedAt(LocalDateTime.now());
    trainingTemplateDao.update(template);

    trainingTemplateItemDao.deleteByTemplateId(id);
    saveItems(id, request.getItems());
    template.setItems(trainingTemplateItemDao.selectByTemplateId(id));
    return ResponseEntity.ok(template);
  }

  @AuditLog(action = "TEMPLATE_DELETE", targetTable = "training_templates")
  @PostMapping("/api/templates/{id}/delete")
  @ResponseBody
  @Transactional
  public ResponseEntity<Void> deleteTemplate(@PathVariable Long id, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    Optional<TrainingTemplate> opt = trainingTemplateDao.selectById(id);
    if (opt.isEmpty()) return ResponseEntity.notFound().build();
    if (!opt.get().getUserId().equals(userId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    trainingTemplateDao.softDeleteById(id);
    return ResponseEntity.ok().build();
  }

  @AuditLog(action = "TEMPLATE_APPLY", targetTable = "training_templates")
  @PostMapping("/api/templates/{id}/apply")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> applyTemplate(
      @PathVariable Long id, @RequestBody Map<String, String> body, Principal principal) {
    String dateStr = body.get("date");
    if (dateStr == null || dateStr.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    LocalDate trainingDate;
    try {
      trainingDate = LocalDate.parse(dateStr);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }

    Long userId = trainingService.getUserIdByEmail(principal.getName());
    Optional<TrainingTemplate> templateOpt = trainingTemplateDao.selectById(id);
    if (templateOpt.isEmpty()) return ResponseEntity.notFound().build();

    TrainingTemplate template = templateOpt.get();
    if (!template.getUserId().equals(userId)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    List<TrainingTemplateItem> items = trainingTemplateItemDao.selectByTemplateId(id);
    if (items.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    // 種目ごとにグループ化（display_order → set_number 順）
    Map<String, List<TrainingTemplateItem>> byItem = new LinkedHashMap<>();
    items.stream()
        .sorted(
            Comparator.comparingInt(TrainingTemplateItem::getDisplayOrder)
                .thenComparingInt(TrainingTemplateItem::getSetNumber))
        .forEach(
            item -> byItem.computeIfAbsent(item.getItemName(), k -> new ArrayList<>()).add(item));

    // 当日の既存種目チェック（重複警告用）
    List<Training> existingToday = trainingService.getFullTrainingData(userId, trainingDate);
    Set<String> existingMenus =
        existingToday.stream().map(Training::getMenu).collect(Collectors.toSet());
    List<String> duplicateItems =
        byItem.keySet().stream().filter(existingMenus::contains).collect(Collectors.toList());

    List<Long> trainingIds = new ArrayList<>();

    for (Map.Entry<String, List<TrainingTemplateItem>> entry : byItem.entrySet()) {
      String itemName = entry.getKey();
      List<TrainingTemplateItem> itemSets = entry.getValue();

      Training training = new Training();
      training.setUserId(userId);
      training.setTrainingDate(trainingDate);
      training.setMenu(itemName);
      training.setPartCode(template.getPartCode());
      training.setIsCompleted(false);
      training.setIsAllCompleted(false);
      training.setCreateDatetime(LocalDateTime.now());
      training.setUpdatedDatetime(LocalDateTime.now());

      // 前回実績（優先）→ テンプレートデフォルト の順で値を決定
      List<Training> recentSessions =
          trainingDao.selectRecentSessionsByItem(userId, itemName, trainingDate, 1);
      List<TrainingDetail> recentDetails =
          recentSessions.isEmpty()
              ? Collections.emptyList()
              : trainingDetailDao.selectByTrainingId(recentSessions.get(0).getId());

      List<TrainingDetail> details = new ArrayList<>();
      for (int i = 0; i < itemSets.size(); i++) {
        TrainingTemplateItem tplItem = itemSets.get(i);
        TrainingDetail detail = new TrainingDetail();
        detail.setSetNumber(i + 1);
        detail.setSetType(tplItem.getSetType() != null ? tplItem.getSetType() : "MAIN");

        if (i < recentDetails.size()) {
          TrainingDetail prev = recentDetails.get(i);
          detail.setWeight(
              prev.getWeight() != null
                  ? prev.getWeight()
                  : (tplItem.getWeight() != null ? tplItem.getWeight().doubleValue() : 0.0));
          detail.setReps(
              prev.getReps() != null
                  ? prev.getReps()
                  : (tplItem.getReps() != null ? tplItem.getReps() : 0));
        } else {
          detail.setWeight(tplItem.getWeight() != null ? tplItem.getWeight().doubleValue() : 0.0);
          detail.setReps(tplItem.getReps() != null ? tplItem.getReps() : 0);
        }
        detail.setIsCompleted(false);
        details.add(detail);
      }

      training.setDetails(details);
      trainingService.save(training, principal);
      if (training.getId() != null) {
        trainingIds.add(training.getId());
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("trainingIds", trainingIds);
    result.put("duplicateItems", duplicateItems);
    return ResponseEntity.ok(result);
  }

  private void saveItems(Long templateId, List<TemplateItemRequest> itemRequests) {
    if (itemRequests == null) return;
    for (int i = 0; i < itemRequests.size(); i++) {
      TemplateItemRequest req = itemRequests.get(i);
      if (req.getItemName() == null || req.getItemName().isBlank()) continue;
      TrainingTemplateItem item = new TrainingTemplateItem();
      item.setTemplateId(templateId);
      item.setItemName(req.getItemName().trim());
      item.setSetNumber(req.getSetNumber() != null ? req.getSetNumber() : i + 1);
      String st = req.getSetType();
      item.setSetType(
          st != null && (st.equals("WARMUP") || st.equals("MAIN") || st.equals("DROP"))
              ? st
              : "MAIN");
      item.setWeight(req.getWeight());
      item.setReps(req.getReps());
      item.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : i);
      trainingTemplateItemDao.insert(item);
    }
  }

  @Data
  public static class TemplateRequest {
    private String name;
    private String partCode;
    private String memo;
    private List<TemplateItemRequest> items;
  }

  @Data
  public static class TemplateItemRequest {
    private String itemName;
    private Integer setNumber;
    private String setType;
    private BigDecimal weight;
    private Integer reps;
    private Integer displayOrder;
  }
}
