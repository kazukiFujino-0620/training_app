package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.AddSetRequest;
import com.example.traning.mobile.dto.AddTrainingRequest;
import com.example.traning.mobile.dto.CompleteTrainingRequest;
import com.example.traning.mobile.dto.SetUpdateResponse;
import com.example.traning.mobile.dto.TrainingHistoryResponse;
import com.example.traning.mobile.dto.UpdateSetRequest;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/training")
@Slf4j
public class MobileTrainingController {

  private final TrainingService trainingService;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final PersonalRecordService personalRecordService;

  public MobileTrainingController(
      TrainingService trainingService,
      TrainingDao trainingDao,
      TrainingDetailDao trainingDetailDao,
      PersonalRecordService personalRecordService) {
    this.trainingService = trainingService;
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.personalRecordService = personalRecordService;
  }

  /** 当日（またはdate指定日）のトレーニング一覧を返す。 各 Training に details リスト（セット情報）が含まれる。 */
  @GetMapping("/today")
  public ResponseEntity<List<Training>> getToday(
      @AuthenticationPrincipal Long userId, @RequestParam(required = false) String date) {

    LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
    List<Training> trainings = trainingService.getFullTrainingData(userId, targetDate);
    return ResponseEntity.ok(trainings);
  }

  /** 当日のトレーニングに種目を追加する。 sets が空でも登録可能（後からセットを追加する想定はなし）。 */
  @PostMapping
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_ADD", targetTable = "trainings")
  public ResponseEntity<Long> addTraining(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AddTrainingRequest req) {

    Training training = new Training();
    training.setUserId(userId);
    training.setMenu(req.getMenu());
    training.setPartCode(req.getPartCode());
    training.setTrainingDate(
        req.getTrainingDate() != null ? req.getTrainingDate() : LocalDate.now());
    training.setMemo(req.getMemo());
    trainingDao.insert(training);

    List<com.example.traning.mobile.dto.AddSetRequest> sets = req.getSets();
    for (int i = 0; i < sets.size(); i++) {
      com.example.traning.mobile.dto.AddSetRequest s = sets.get(i);
      TrainingDetail detail = new TrainingDetail();
      detail.setTrainingId(training.getId());
      detail.setSetNumber(i + 1);
      detail.setWeight(s.getWeight());
      detail.setReps(s.getReps());
      detail.setCount(s.getReps());
      detail.setSetType(s.getSetType() != null ? s.getSetType() : "MAIN");
      trainingDetailDao.insert(detail);
    }

    return ResponseEntity.status(201).body(training.getId());
  }

  /** 種目をソフトデリートする（自分のトレーニングのみ） */
  @DeleteMapping("/{id}")
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_DELETE", targetTable = "trainings")
  public ResponseEntity<Void> deleteTraining(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {

    Training training = trainingDao.selectById(id);
    if (training == null) return ResponseEntity.notFound().build();
    if (!userId.equals(training.getUserId())) return ResponseEntity.status(403).build();

    trainingDetailDao.softDeleteByTrainingId(id);
    trainingDao.softDeleteById(id);
    return ResponseEntity.noContent().build();
  }

  /** 既存トレーニングにセットを1件追加する */
  @PostMapping("/{trainingId}/sets")
  @Transactional
  @AuditLog(action = "MOBILE_SET_ADD", targetTable = "training_details")
  public ResponseEntity<TrainingDetail> addSet(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long trainingId,
      @Valid @RequestBody AddSetRequest req) {

    Training training = trainingDao.selectById(trainingId);
    if (training == null) return ResponseEntity.notFound().build();
    if (!userId.equals(training.getUserId())) return ResponseEntity.status(403).build();

    List<TrainingDetail> existing = trainingDetailDao.selectByTrainingId(trainingId);
    int nextSetNumber =
        existing.stream().mapToInt(TrainingDetail::getSetNumber).max().orElse(0) + 1;

    TrainingDetail detail = new TrainingDetail();
    detail.setTrainingId(trainingId);
    detail.setSetNumber(nextSetNumber);
    detail.setWeight(req.getWeight());
    detail.setReps(req.getReps());
    detail.setCount(req.getReps());
    detail.setSetType(req.getSetType() != null ? req.getSetType() : "MAIN");
    trainingDetailDao.insert(detail);

    return ResponseEntity.status(201).body(detail);
  }

  /** セットを1件削除する（ソフトデリート・最後の1セットは削除不可） */
  @DeleteMapping("/sets/{id}")
  @Transactional
  @AuditLog(action = "MOBILE_SET_DELETE", targetTable = "training_details")
  public ResponseEntity<Void> deleteSet(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {

    TrainingDetail detail = trainingDetailDao.selectById(id);
    if (detail == null) return ResponseEntity.notFound().build();

    Training training = trainingDao.selectById(detail.getTrainingId());
    if (training == null || !userId.equals(training.getUserId())) {
      return ResponseEntity.status(403).build();
    }

    List<TrainingDetail> existing = trainingDetailDao.selectByTrainingId(detail.getTrainingId());
    if (existing.size() <= 1) {
      throw new IllegalArgumentException("最後のセットは削除できません");
    }

    trainingDetailDao.softDeleteById(id);
    return ResponseEntity.noContent().build();
  }

  /** セットの完了フラグ・重量・回数を一括更新し、PR更新チェックも行う。 フィールドは null で送ると更新スキップ。 */
  @PatchMapping("/sets/{id}")
  @Transactional
  @AuditLog(action = "MOBILE_SET_UPDATE", targetTable = "training_details")
  public ResponseEntity<SetUpdateResponse> updateSet(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateSetRequest req) {

    TrainingDetail detail = trainingDetailDao.selectById(id);
    if (detail == null) return ResponseEntity.notFound().build();

    Training training = trainingDao.selectById(detail.getTrainingId());
    if (training == null || !userId.equals(training.getUserId())) {
      return ResponseEntity.status(403).build();
    }

    if (req.getWeight() != null) detail.setWeight(req.getWeight());
    if (req.getReps() != null) {
      detail.setReps(req.getReps());
      detail.setCount(req.getReps());
    }
    if (req.getIsCompleted() != null) detail.setIsCompleted(req.getIsCompleted());
    detail.setUpdatedDatetime(LocalDateTime.now());
    trainingDetailDao.update(detail);

    // PR更新チェック（セット完了かつ重量・回数が指定された場合）
    boolean isPR = false;
    String prMessage = null;
    if (Boolean.TRUE.equals(req.getIsCompleted())
        && req.getWeight() != null
        && req.getWeight() > 0
        && req.getReps() != null) {
      try {
        Optional<PersonalRecord> before =
            personalRecordService.getByUserIdAndItem(userId, training.getMenu());
        personalRecordService.updateIfBetter(
            userId, training.getMenu(), req.getWeight(), req.getReps(), LocalDate.now());
        if (before.isEmpty() || req.getWeight() > before.get().getMaxWeight()) {
          isPR = true;
          prMessage = training.getMenu() + " 新記録！ " + req.getWeight() + "kg × " + req.getReps();
        }
      } catch (Exception e) {
        log.warn("PR更新チェック失敗: userId={}, item={}", userId, training.getMenu(), e);
      }
    }

    boolean completed =
        req.getIsCompleted() != null ? req.getIsCompleted() : detail.getIsCompleted();
    return ResponseEntity.ok(new SetUpdateResponse(id, completed, isPR, prMessage));
  }

  /**
   * トレーニング全体を完了済みにする。 durationSec が指定された場合、対象トレーニングと同じ日付・同一ユーザーの 全トレーニングの duration カラムに秒数の文字列を保存する。
   * これによりモバイル側は再開時に MAX(duration) を読み出して経過時間を復元できる。
   */
  @PostMapping("/complete")
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_COMPLETE", targetTable = "trainings")
  public ResponseEntity<Void> completeTraining(
      @AuthenticationPrincipal Long userId, @RequestBody CompleteTrainingRequest body) {

    Long trainingId = body.getTrainingId();
    if (trainingId == null) return ResponseEntity.badRequest().build();

    Training training = trainingDao.selectById(trainingId);
    if (training == null) return ResponseEntity.notFound().build();
    if (!userId.equals(training.getUserId())) return ResponseEntity.status(403).build();

    LocalDateTime now = LocalDateTime.now();
    training.setIsAllCompleted(true);
    training.setUpdatedDatetime(now);

    Integer durationSec = body.getDurationSec();
    if (durationSec != null) {
      // 当日の自分のトレーニング全件に経過秒数を保存（MAX(duration)で復元できるように）
      LocalDate targetDate = training.getTrainingDate();
      int h = durationSec / 3600;
      int m = (durationSec % 3600) / 60;
      int s = durationSec % 60;
      String durationStr = String.format("%02d:%02d:%02d", h, m, s);
      List<Training> todays = trainingDao.selectByDate(userId, targetDate, targetDate);
      for (Training t : todays) {
        if (trainingId.equals(t.getId())) {
          // 完了対象は上で更新したインスタンスを使う
          training.setDuration(durationStr);
          continue;
        }
        t.setDuration(durationStr);
        t.setUpdatedDatetime(now);
        trainingDao.update(t);
      }
    }

    trainingDao.update(training);
    return ResponseEntity.noContent().build();
  }

  // ── 既存エンドポイント（後方互換） ──────────────────────────────────────

  /** セット完了フラグのみ更新（後方互換用） */
  @PutMapping("/sets/{id}/complete")
  @Transactional
  public ResponseEntity<Void> completeSet(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody java.util.Map<String, Boolean> body) {

    TrainingDetail detail = trainingDetailDao.selectById(id);
    if (detail == null) return ResponseEntity.notFound().build();
    if (!isOwnedByUser(detail, userId)) return ResponseEntity.status(403).build();

    Boolean completed = body.get("completed");
    if (completed == null) return ResponseEntity.badRequest().build();

    detail.setIsCompleted(completed);
    detail.setUpdatedDatetime(LocalDateTime.now());
    trainingDetailDao.update(detail);
    return ResponseEntity.noContent().build();
  }

  /** 重量・回数のみ更新（後方互換用） */
  @PutMapping("/sets/{id}/update")
  @Transactional
  public ResponseEntity<Void> updateSetLegacy(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateSetRequest req) {

    TrainingDetail detail = trainingDetailDao.selectById(id);
    if (detail == null) return ResponseEntity.notFound().build();
    if (!isOwnedByUser(detail, userId)) return ResponseEntity.status(403).build();

    if (req.getWeight() != null) detail.setWeight(req.getWeight());
    if (req.getReps() != null) {
      detail.setReps(req.getReps());
      detail.setCount(req.getReps());
    }
    detail.setUpdatedDatetime(LocalDateTime.now());
    trainingDetailDao.update(detail);
    return ResponseEntity.noContent().build();
  }

  /** 種目名で過去のトレーニング記録を取得する（前回記録表示用）。最大10件取得。 */
  @GetMapping("/history")
  public ResponseEntity<List<TrainingHistoryResponse>> getTrainingHistory(
      @AuthenticationPrincipal Long userId,
      @RequestParam String itemName) {

    List<Training> sessions = trainingDao.selectRecentSessionsByItem(
        userId, itemName, LocalDate.now().plusDays(1), 10);

    List<TrainingHistoryResponse> result = sessions.stream()
        .map(session -> {
          List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(session.getId());
          List<TrainingHistoryResponse.SetRecord> setRecords = details.stream()
              .filter(d -> d.getDeletedAt() == null)
              .sorted(java.util.Comparator.comparingInt(TrainingDetail::getSetNumber))
              .map(d -> new TrainingHistoryResponse.SetRecord(
                  d.getSetNumber(), d.getWeight(), d.getReps()))
              .toList();
          String dateStr = session.getTrainingDate()
              .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"));
          return new TrainingHistoryResponse(dateStr, setRecords);
        })
        .filter(h -> !h.getSets().isEmpty())
        .toList();

    return ResponseEntity.ok(result);
  }

  private boolean isOwnedByUser(TrainingDetail detail, Long userId) {
    Training training = trainingDao.selectById(detail.getTrainingId());
    return training != null && userId.equals(training.getUserId());
  }
}
