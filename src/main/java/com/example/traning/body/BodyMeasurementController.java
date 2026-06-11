package com.example.traning.body;

import com.example.traning.audit.AuditLog;
import com.example.traning.training.service.TrainingService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BodyMeasurementController {

  private final BodyMeasurementService bodyMeasurementService;
  private final TrainingService trainingService;

  @GetMapping("/user/body")
  public String showBody(Model model, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    List<BodyMeasurement> measurements = bodyMeasurementService.getAll(userId);
    model.addAttribute("measurements", measurements);
    return "user/body";
  }

  @AuditLog(action = "BODY_MEASUREMENT_SAVE", targetTable = "body_measurements")
  @PostMapping("/api/body")
  @ResponseBody
  public ResponseEntity<String> save(@RequestBody Map<String, Object> body, Principal principal) {
    try {
      Long userId = trainingService.getUserIdByEmail(principal.getName());
      LocalDate date = LocalDate.parse((String) body.get("measuredDate"));
      Double weightKg = ((Number) body.get("weightKg")).doubleValue();
      // バリデーション
      if (weightKg < 20.0 || weightKg > 300.0) {
        return ResponseEntity.badRequest().body("体重は20〜300kgの範囲で入力してください");
      }
      Double bodyFatPct =
          body.get("bodyFatPct") != null ? ((Number) body.get("bodyFatPct")).doubleValue() : null;
      if (bodyFatPct != null && (bodyFatPct < 0.0 || bodyFatPct > 60.0)) {
        return ResponseEntity.badRequest().body("体脂肪率は0〜60%の範囲で入力してください");
      }
      String memo = (String) body.get("memo");
      if (memo != null && memo.length() > 200) {
        return ResponseEntity.badRequest().body("メモは200文字以内で入力してください");
      }
      // 未来日チェック
      if (date.isAfter(LocalDate.now())) {
        return ResponseEntity.badRequest().body("日付は今日以前を指定してください");
      }
      bodyMeasurementService.save(userId, date, weightKg, bodyFatPct, memo);
      return ResponseEntity.ok("保存しました");
    } catch (Exception e) {
      return ResponseEntity.internalServerError().body("保存に失敗しました");
    }
  }

  @AuditLog(action = "BODY_MEASUREMENT_DELETE", targetTable = "body_measurements")
  @DeleteMapping("/api/body/{id}")
  @ResponseBody
  public ResponseEntity<String> delete(@PathVariable Long id, Principal principal) {
    Long userId = trainingService.getUserIdByEmail(principal.getName());
    bodyMeasurementService.delete(id, userId);
    return ResponseEntity.ok("削除しました");
  }
}
