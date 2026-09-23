package com.example.traning.periodization;

import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dao.TrainingDetailDao.SessionAggregate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 停滞検知（ディロード週の任意提案）。機能見直し-1-#3 詳細設計書3-3節、QA Q3-4（training-coordinator確認済みのハイブリッド方式）。
 *
 * <ul>
 *   <li>指標A（主軸）: 同一種目の合計ボリューム（重量×回数の総和）。直近3回のセッションのうち2回以上で、 直前のセッションより増加していなければ停滞。
 *   <li>指標B（補助）: 同一種目の使用重量の最大値。直近2回連続で直前のセッションより増加していなければ停滞。
 *   <li>Aのみ停滞=MILD（穏やかな案内）、AとBの両方が停滞=STRONG（確信度の高い案内）。いずれも提案のみで強制はしない。
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class StagnationDetectionService {

  private final TrainingDetailDao trainingDetailDao;

  /** QA Q3-4確定: 判定窓は直近3回のセッション。 */
  static final int WINDOW_SIZE = 3;

  /** QA Q3-4確定: 指標Aは判定窓3回中2回以上の停滞で成立（多数決）。 */
  static final int VOLUME_STAGNANT_MIN_COUNT = 2;

  /** 設計書3-3節: 指標Bは直近2回連続で更新なしのときに成立。 */
  static final int MAX_WEIGHT_CONSECUTIVE = 2;

  public enum StagnationLevel {
    NONE,
    MILD,
    STRONG
  }

  @Transactional(readOnly = true)
  public StagnationLevel evaluate(Long userId, String itemName) {
    // 比較対象として判定窓の1つ前のセッションも必要なため WINDOW_SIZE + 1 件取得する
    List<SessionAggregate> recentFirst =
        trainingDetailDao.selectRecentSessionAggregatesByItem(userId, itemName, WINDOW_SIZE + 1);
    List<SessionAggregate> sessions = new ArrayList<>(recentFirst);
    Collections.reverse(sessions); // 古い順に並べ替え
    return evaluateSessions(sessions);
  }

  /**
   * 古い順に並んだセッション集計から停滞レベルを判定する（単体テスト用に分離）。
   *
   * @param sessions 古い順。WINDOW_SIZE + 1件に満たない場合は母数不足としてNONE
   */
  static StagnationLevel evaluateSessions(List<SessionAggregate> sessions) {
    if (sessions == null || sessions.size() < WINDOW_SIZE + 1) {
      return StagnationLevel.NONE;
    }
    List<SessionAggregate> window =
        sessions.subList(sessions.size() - (WINDOW_SIZE + 1), sessions.size());

    boolean volumeStagnant = isVolumeStagnant(window);
    boolean maxWeightStagnant = isMaxWeightStagnant(window);

    if (volumeStagnant && maxWeightStagnant) return StagnationLevel.STRONG;
    if (volumeStagnant) return StagnationLevel.MILD;
    return StagnationLevel.NONE;
  }

  /** 判定窓3回のうち、直前のセッションより合計ボリュームが増加していない回が2回以上あるか。 */
  private static boolean isVolumeStagnant(List<SessionAggregate> window) {
    int stagnantCount = 0;
    for (int i = 1; i < window.size(); i++) {
      if (value(window.get(i).totalVolume) <= value(window.get(i - 1).totalVolume)) {
        stagnantCount++;
      }
    }
    return stagnantCount >= VOLUME_STAGNANT_MIN_COUNT;
  }

  /** 直近2回連続で、直前のセッションより最大重量が増加していないか。 */
  private static boolean isMaxWeightStagnant(List<SessionAggregate> window) {
    int last = window.size() - 1;
    for (int k = 0; k < MAX_WEIGHT_CONSECUTIVE; k++) {
      int i = last - k;
      if (value(window.get(i).maxWeight) > value(window.get(i - 1).maxWeight)) {
        return false;
      }
    }
    return true;
  }

  private static double value(Double d) {
    return d != null ? d : 0.0;
  }
}
