package com.example.traning.restpreference;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.prediction.OneRmPredictionService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 機能見直し-1-#1: 種目別・自動休憩タイマーの反映ロジック。
 *
 * <p>休憩時間は以下の優先順位で決定する。
 *
 * <ol>
 *   <li>ユーザー個人の上書き設定（{@code user_item_rest_preferences}。PR#169）があれば最優先で採用する。
 *   <li>無ければ「複合/単関節区分 × 推定1RMに対する当日セット重量の割合」で算出する。
 * </ol>
 *
 * <p>詳細設計: [[training-app/詳細設計/機能見直し-1-#1 -
 * 種目別・自動休憩タイマーの反映ロジック確定_設計書]]。単関節種目の秒数（一律30秒短縮）はtraining-coordinatorへの正式確認を経て確定した正式値であり、
 * 暫定値ではない（設計書5節参照）。
 */
@Service
@RequiredArgsConstructor
public class RestIntervalCalculationService {

  // 複合種目の休憩秒数（会議確定値）。
  private static final int COMPOUND_REST_SECONDS_HIGH = 180; // 3分（85%以上）
  private static final int COMPOUND_REST_SECONDS_MID = 120; // 2分（65〜85%）
  private static final int COMPOUND_REST_SECONDS_LOW = 90; // 1.5分（65%未満）

  // 単関節種目の休憩秒数（正式値・確定済み。複合種目の基準から一律30秒短縮）。
  private static final int ISOLATION_REST_SECONDS_HIGH = 150; // 2分30秒
  private static final int ISOLATION_REST_SECONDS_MID = 90; // 1分30秒
  private static final int ISOLATION_REST_SECONDS_LOW = 60; // 1分（実用下限60秒ちょうど）

  private static final double RATIO_THRESHOLD_HIGH = 0.85;
  private static final double RATIO_THRESHOLD_MID = 0.65;

  private final RestPreferenceService restPreferenceService;
  private final OneRmPredictionService oneRmPredictionService;
  private final TrainingMasterDao trainingMasterDao;
  private final PersonalRecordService personalRecordService;

  /**
   * 休憩時間(秒)を解決する。
   *
   * @param userId ユーザーID
   * @param itemName 種目名
   * @param weight 当日セットの使用重量(kg)
   * @param reps 当日セットの反復回数
   * @return 休憩推奨秒数
   */
  public int resolveIntervalSeconds(Long userId, String itemName, double weight, int reps) {
    Optional<UserItemRestPreference> pref = restPreferenceService.find(userId, itemName);
    if (pref.isPresent()) {
      return pref.get().getRestSeconds();
    }
    return calculateSystemRecommendedSeconds(userId, itemName, weight, reps);
  }

  private int calculateSystemRecommendedSeconds(
      Long userId, String itemName, double weight, int reps) {
    Optional<PersonalRecord> pr = personalRecordService.getByUserIdAndItem(userId, itemName);
    double estimatedOneRm =
        pr.isPresent()
            ? oneRmPredictionService.estimateOneRm(pr.get().getMaxWeight(), pr.get().getMaxReps())
            // 初回セット（PR未登録）は当日のセット自体を暫定PRとみなして推定1RMを算出する
            // （置き換え前ロジックがPR未登録時に当日重量をmaxWeightとして扱っていたのと同じ考え方）。
            : oneRmPredictionService.estimateOneRm(weight, reps);

    if (estimatedOneRm <= 0) return RestPreferenceService.DEFAULT_REST_SECONDS;

    double ratio = weight / estimatedOneRm;
    boolean isCompound = resolveIsCompound(itemName);

    if (isCompound) {
      if (ratio >= RATIO_THRESHOLD_HIGH) return COMPOUND_REST_SECONDS_HIGH;
      if (ratio >= RATIO_THRESHOLD_MID) return COMPOUND_REST_SECONDS_MID;
      return COMPOUND_REST_SECONDS_LOW;
    } else {
      if (ratio >= RATIO_THRESHOLD_HIGH) return ISOLATION_REST_SECONDS_HIGH;
      if (ratio >= RATIO_THRESHOLD_MID) return ISOLATION_REST_SECONDS_MID;
      return ISOLATION_REST_SECONDS_LOW;
    }
  }

  private boolean resolveIsCompound(String itemName) {
    return trainingMasterDao
        .selectByItemName(itemName)
        .map(TrainingItemMaster::getIsCompound)
        .orElse(true);
  }
}
