package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.mobile.dto.AddSetRequest;
import com.example.traning.mobile.dto.AddTrainingRequest;
import com.example.traning.mobile.dto.CompleteTrainingRequest;
import com.example.traning.mobile.dto.SetUpdateResponse;
import com.example.traning.mobile.dto.TrainingCalorieResponse;
import com.example.traning.mobile.dto.TrainingHistoryResponse;
import com.example.traning.mobile.dto.UpdateSetRequest;
import com.example.traning.mobile.dto.UpdateTrainingMemoRequest;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.SetType;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
  private final UserDao userDao;
  private final TrainingMasterDao trainingMasterDao;
  private final CalorieCalculator calorieCalculator;

  public MobileTrainingController(
      TrainingService trainingService,
      TrainingDao trainingDao,
      TrainingDetailDao trainingDetailDao,
      PersonalRecordService personalRecordService,
      UserDao userDao,
      TrainingMasterDao trainingMasterDao,
      CalorieCalculator calorieCalculator) {
    this.trainingService = trainingService;
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
    this.personalRecordService = personalRecordService;
    this.userDao = userDao;
    this.trainingMasterDao = trainingMasterDao;
    this.calorieCalculator = calorieCalculator;
  }

  /** 当日（またはdate指定日）のトレーニング一覧を返す。 各 Training に details リスト（セット情報）が含まれる。 */
  @GetMapping("/today")
  public ResponseEntity<List<Training>> getToday(
      @AuthenticationPrincipal Long userId, @RequestParam(required = false) String date) {

    LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
    List<Training> trainings = trainingService.getFullTrainingData(userId, targetDate);
    return ResponseEntity.ok(trainings);
  }

  /**
   * 当日（またはdate指定日）の推定消費カロリーを返す（ita2-3）。 その日の全種目が完了済み（isAllCompleted）の場合のみ計算・返却し、
   * 未完了があればavailable=falseを返す。
   */
  @GetMapping("/today/calories")
  public ResponseEntity<TrainingCalorieResponse> getTodayCalories(
      @AuthenticationPrincipal Long userId, @RequestParam(required = false) String date) {

    LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
    List<Training> trainings = trainingService.getFullTrainingData(userId, targetDate);

    boolean allCompleted =
        !trainings.isEmpty() && trainings.stream().allMatch(Training::isAllCompleted);
    if (!allCompleted) {
      return ResponseEntity.ok(new TrainingCalorieResponse(false, null));
    }

    Map<String, TrainingItemMaster> itemMasterByName =
        trainingMasterDao.selectAllItems().stream()
            .collect(Collectors.toMap(TrainingItemMaster::getItemName, item -> item, (a, b) -> a));
    CalorieCalculator.CalorieEstimate estimate =
        calorieCalculator.estimate(trainings, itemMasterByName);

    boolean available = estimate.type == CalorieCalculator.CalorieType.CALCULATED;
    return ResponseEntity.ok(new TrainingCalorieResponse(available, estimate.calories));
  }

  /** 当日のトレーニングに種目を追加する。 sets が空でも登録可能（後からセットを追加する想定はなし）。 */
  @PostMapping
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_ADD", targetTable = "trainings")
  public ResponseEntity<Long> addTraining(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody AddTrainingRequest req) {

    Training training = new Training();
    training.setUserId(userId);
    // organization_idはDB上NOT NULL。所有ユーザーの所属組織から解決して設定する。
    training.setOrganizationId(userDao.selectOrganizationIdById(userId));
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
      detail.setSetType(SetType.fromValueOrMain(s.getSetType()).name());
      trainingDetailDao.insert(detail);
    }

    return ResponseEntity.status(201).body(training.getId());
  }

  /** トレーニングのメモを更新する（ita4-4、自分のトレーニングのみ）。 */
  @PatchMapping("/{id}/memo")
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_MEMO_UPDATE", targetTable = "trainings")
  public ResponseEntity<Void> updateMemo(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody UpdateTrainingMemoRequest req) {

    Training training = trainingDao.selectById(id);
    if (training == null) return ResponseEntity.notFound().build();
    if (!userId.equals(training.getUserId())) return ResponseEntity.status(403).build();

    trainingDao.updateMemoById(id, req.getMemo(), LocalDateTime.now());
    return ResponseEntity.noContent().build();
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
    detail.setSetType(SetType.fromValueOrMain(req.getSetType()).name());
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
    // 有酸素運動（ita2-1）固有項目。筋トレ種目では常にnullのまま送られてくるため更新スキップされる。
    if (req.getDurationMin() != null) detail.setDurationMin(req.getDurationMin());
    if (req.getDistanceKm() != null) detail.setDistanceKm(req.getDistanceKm());
    if (req.getAvgHeartRateBpm() != null) detail.setAvgHeartRateBpm(req.getAvgHeartRateBpm());
    if (req.getCaloriesKcal() != null) detail.setCaloriesKcal(req.getCaloriesKcal());
    detail.setUpdatedDatetime(LocalDateTime.now());
    trainingDetailDao.update(detail);

    // PR更新チェック（セット完了かつ重量・回数が指定された場合。有酸素運動はweightを送らないため自然に対象外）
    boolean isPR = false;
    String prMessage = null;
    Integer recommendedIntervalSeconds = null;
    if (Boolean.TRUE.equals(req.getIsCompleted())
        && req.getWeight() != null
        && req.getWeight() > 0
        && req.getReps() != null) {
      try {
        Optional<PersonalRecord> before =
            personalRecordService.getByUserIdAndItem(userId, training.getMenu());

        double maxWeight =
            before.isPresent()
                ? Math.max(before.get().getMaxWeight(), req.getWeight())
                : req.getWeight();
        recommendedIntervalSeconds =
            calculateRecommendedIntervalSeconds(req.getWeight(), maxWeight);

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
    return ResponseEntity.ok(
        new SetUpdateResponse(id, completed, isPR, prMessage, recommendedIntervalSeconds));
  }

  /**
   * F4: インターバル推奨通知。 重量 / 自己ベスト重量の比率で負荷を3段階に分類し、休憩時間を提案する。
   * WHOOP・Polar・Garmin等が特許を持つ生体信号ベースの回復時間推定とは異なり、 単純な重量比率のみを使う静的カテゴリ分けのため特許抵触リスクなし
   * （feature-gap-analysis §2-2 の代案方針に準拠）。
   */
  private Integer calculateRecommendedIntervalSeconds(double weight, double maxWeight) {
    if (maxWeight <= 0) return null;
    double ratio = weight / maxWeight;
    if (ratio >= 0.85) return 180;
    if (ratio >= 0.6) return 90;
    return 60;
  }

  /**
   * トレーニング全体の完了状態を更新する。 対象トレーニングと同じ日付・同一ユーザーの全トレーニングについて、
   * 種目ごとにセット（論理削除済みを除く）の完了状況をサーバー側で判定し、1件以上のセットが存在し かつ全セットが完了している場合のみ is_all_completed を true
   * にする（未完了セットがあれば false で上書きする）。 durationSec が指定された場合は、当日全トレーニングの duration カラムに HH:MM:SS
   * 形式の文字列を保存する（未指定時は duration を更新しない）。 これによりモバイル側は再開時に MAX(duration) を読み出して経過時間を復元できる。
   */
  @PostMapping("/complete")
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_COMPLETE", targetTable = "trainings")
  public ResponseEntity<Void> completeTraining(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CompleteTrainingRequest body) {

    Long trainingId = body.getTrainingId();
    if (trainingId == null) return ResponseEntity.badRequest().build();

    Training training = trainingDao.selectById(trainingId);
    if (training == null) return ResponseEntity.notFound().build();
    if (!userId.equals(training.getUserId())) return ResponseEntity.status(403).build();

    LocalDateTime now = LocalDateTime.now();

    // durationSec 指定時のみ HH:MM:SS 形式に変換（未指定時は null のまま duration を更新しない）
    Integer durationSec = body.getDurationSec();
    String durationStr = null;
    if (durationSec != null) {
      int h = durationSec / 3600;
      int m = (durationSec % 3600) / 60;
      int s = durationSec % 60;
      durationStr = String.format("%02d:%02d:%02d", h, m, s);
    }

    // 当日・同一ユーザーの全トレーニングを対象に、セットの完了状況から is_all_completed を判定して更新する
    LocalDate targetDate = training.getTrainingDate();
    List<Training> todays = trainingDao.selectByDate(userId, targetDate, targetDate);
    for (Training t : todays) {
      // selectByTrainingId は論理削除済み（deleted_at IS NOT NULL）のセットを除外している
      List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(t.getId());
      boolean isAllCompleted =
          !details.isEmpty() && details.stream().allMatch(TrainingDetail::getIsCompleted);
      trainingDao.updateCompletionById(t.getId(), durationStr, isAllCompleted, now);
    }

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
      @AuthenticationPrincipal Long userId, @RequestParam String itemName) {

    List<Training> sessions =
        trainingDao.selectRecentSessionsByItem(userId, itemName, LocalDate.now().plusDays(1), 10);

    List<TrainingHistoryResponse> result =
        sessions.stream()
            .map(
                session -> {
                  List<TrainingDetail> details =
                      trainingDetailDao.selectByTrainingId(session.getId());
                  List<TrainingHistoryResponse.SetRecord> setRecords =
                      details.stream()
                          .filter(d -> d.getDeletedAt() == null)
                          .sorted(java.util.Comparator.comparingInt(TrainingDetail::getSetNumber))
                          .map(
                              d ->
                                  new TrainingHistoryResponse.SetRecord(
                                      d.getSetNumber(), d.getWeight(), d.getReps()))
                          .toList();
                  String dateStr =
                      session
                          .getTrainingDate()
                          .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"));
                  return new TrainingHistoryResponse(dateStr, setRecords);
                })
            .filter(h -> !h.getSets().isEmpty())
            .toList();

    return ResponseEntity.ok(result);
  }

  // ── スーパーセット（F-M2） ────────────────────────────────────────────────

  /** 当日の未グループ化種目一覧を取得する（ペアリング候補）。 */
  @GetMapping("/superset/candidates")
  public ResponseEntity<List<Training>> getSupersetCandidates(
      @AuthenticationPrincipal Long userId, @RequestParam String date) {
    LocalDate targetDate = LocalDate.parse(date);
    return ResponseEntity.ok(trainingService.getCandidatesForSuperset(userId, targetDate));
  }

  /** 2種目をスーパーセットとしてグループ化する。 */
  @PostMapping("/superset/group")
  @Transactional
  @AuditLog(action = "MOBILE_SUPERSET_GROUP", targetTable = "trainings")
  public ResponseEntity<?> groupSuperset(
      @AuthenticationPrincipal Long userId, @RequestBody java.util.Map<String, List<Long>> body) {
    List<Long> trainingIds = body.get("trainingIds");
    try {
      Long groupId = trainingService.groupSuperset(trainingIds, userId);
      return ResponseEntity.ok(java.util.Map.of("supersetGroupId", groupId));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
    }
  }

  /** スーパーセットのグループ化を解除する。 */
  @PostMapping("/superset/ungroup")
  @Transactional
  @AuditLog(action = "MOBILE_SUPERSET_UNGROUP", targetTable = "trainings")
  public ResponseEntity<?> ungroupSuperset(
      @AuthenticationPrincipal Long userId, @RequestBody java.util.Map<String, Long> body) {
    Long supersetGroupId = body.get("supersetGroupId");
    if (supersetGroupId == null) return ResponseEntity.badRequest().build();
    try {
      trainingService.ungroupSuperset(supersetGroupId, userId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
    }
  }

  /**
   * itバグ-10: トレーニング順の変更（並び替え）。渡された順に{@code display_order}を振り直す。
   * 当日の対象トレーニング全件のIDを、希望の並び順で渡すこと（部分的な入れ替えでも全件分の配列を渡す）。
   */
  @PostMapping("/reorder")
  @Transactional
  @AuditLog(action = "MOBILE_TRAINING_REORDER", targetTable = "trainings")
  public ResponseEntity<?> reorder(
      @AuthenticationPrincipal Long userId, @RequestBody List<Long> orderedIds) {
    try {
      trainingService.reorderTrainings(orderedIds, userId);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
          .body(java.util.Map.of("message", e.getMessage()));
    }
  }

  private boolean isOwnedByUser(TrainingDetail detail, Long userId) {
    Training training = trainingDao.selectById(detail.getTrainingId());
    return training != null && userId.equals(training.getUserId());
  }
}
