package com.example.traning.periodization;

import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dao.TrainingDetailDao.SessionAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 停滞検知（ディロード週の任意提案）。機能見直し-1-#3 詳細設計書3-3節、QA Q3-4（2026-09-19確定、2026-09-24比較方法の訂正）。
 *
 * <p>USER承認を得て、training-coordinatorの訂正（会議録#3の2026-09-24追記）に基づく方針で作成した。
 *
 * <ul>
 *   <li>直近3回（ディロード週のセッションを除く）を判定窓とする。強度を理由に判定窓から除外しない。
 *   <li>判定窓の各セッションの比較相手は、それより古いセッションのうち計画強度(%1RM)が同じもので最も新しい1件。
 *       期分けプログラム外のセッション（計画強度なし）は、判定窓の直前にある計画外のセッションを比較相手にする。
 *   <li>判定窓の3件とも比較相手を上回っていなければ、その指標は停滞。A（合計ボリューム、主軸）・B（最大重量、補助）とも同じ比べ方。
 *       Aのみ停滞=MILD、A・B両方=STRONG。いずれも提案のみで強制はしない。
 *   <li>さかのぼっても比較相手が見つからないセッションが判定窓に1件でもあれば判定不可（最初の1サイクルは判定不可になるが許容）。
 *   <li>判定は記録保存時に行って結果を保持する（{@link #evaluateAndStore}）。表示側は保持した結果を読む。
 * </ul>
 *
 * <p>確立した標準ではなく、現有データで実装しやすい合理的な近似。誤検知が多ければ見直す。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StagnationDetectionService {

  private final TrainingDetailDao trainingDetailDao;
  private final ProgramCycleDao programCycleDao;
  private final ItemStagnationEvaluationDao itemStagnationEvaluationDao;

  /** QA Q3-4確定: 判定窓は直近3回のセッション。 */
  static final int WINDOW_SIZE = 3;

  /** 比較相手を探してさかのぼる最大セッション数（QA Q3-4 2026-09-24訂正: 30件で見つからなければ判定不可）。 */
  static final int MAX_LOOKBACK_SESSIONS = 30;

  /** 計画強度が「同じ」とみなす許容幅（%1RMのポイント）。確定方針は完全一致（0）。 将来±2.5ポイント程度の許容幅を入れられるよう定数化している。 */
  static final BigDecimal INTENSITY_MATCH_TOLERANCE_PCT = BigDecimal.ZERO;

  public enum StagnationLevel {
    NONE,
    MILD,
    STRONG,
    /** 記録不足、または判定窓に比較相手の見つからないセッションがあり判定できない（提案を出さない）。 */
    INSUFFICIENT
  }

  /** 1セッション分の判定材料。plannedIntensityPctは期分けプログラム外ならnull。 */
  record Session(
      LocalDate date,
      double totalVolume,
      double maxWeight,
      boolean deload,
      BigDecimal plannedIntensityPct) {}

  /**
   * 記録保存時に呼び、判定して結果を保持する。保存処理自体を失敗させないよう、例外はここで握って警告ログのみ出す
   * （呼び出し元のトランザクションに参加した場合もロールバック指定が付かないよう、このメソッド内で処理する）。
   */
  @Transactional
  public void evaluateAndStore(Long userId, String itemName) {
    if (userId == null || itemName == null || itemName.isBlank()) return;
    try {
      StagnationLevel level = evaluate(userId, itemName);
      itemStagnationEvaluationDao.upsert(userId, itemName, level.name(), LocalDateTime.now());
    } catch (Exception e) {
      log.warn("停滞判定に失敗: userId={}, item={}, message={}", userId, itemName, e.getMessage());
    }
  }

  /** 保持している判定結果（種目名→レベル）。未判定の種目は含まない。 */
  @Transactional(readOnly = true)
  public Map<String, StagnationLevel> getStoredLevels(Long userId, List<String> itemNames) {
    Map<String, StagnationLevel> result = new HashMap<>();
    if (itemNames == null || itemNames.isEmpty()) return result;
    for (ItemStagnationEvaluation e :
        itemStagnationEvaluationDao.selectByUserIdAndItemNames(userId, itemNames)) {
      result.put(e.getItemName(), StagnationLevel.valueOf(e.getLevel()));
    }
    return result;
  }

  /** 現時点の記録から判定する（保持はしない）。 */
  @Transactional(readOnly = true)
  public StagnationLevel evaluate(Long userId, String itemName) {
    List<SessionAggregate> recentFirst =
        trainingDetailDao.selectRecentSessionAggregatesByItem(
            userId, itemName, MAX_LOOKBACK_SESSIONS);
    if (recentFirst.size() <= WINDOW_SIZE) return StagnationLevel.INSUFFICIENT;

    LocalDate newest = recentFirst.get(0).trainingDate;
    LocalDate oldest = recentFirst.get(recentFirst.size() - 1).trainingDate;
    PlanLookup plan = new PlanLookup(programCycleDao, userId, oldest, newest);

    List<Session> sessions = new ArrayList<>();
    for (SessionAggregate a : recentFirst) {
      PlanLookup.WeekPlan wp = plan.find(a.trainingDate);
      sessions.add(
          new Session(
              a.trainingDate,
              value(a.totalVolume),
              value(a.maxWeight),
              wp != null && wp.deload(),
              wp != null ? wp.intensityPct() : null));
    }
    return evaluateSessions(sessions);
  }

  /**
   * 新しい順に並んだセッションから停滞レベルを判定する（単体テスト用に分離）。
   *
   * <p>手順: (1) ディロード週のセッションを除く。(2) 先頭3件を判定窓とする（強度では除外しない）。 (3) 判定窓の各セッションについて比較相手を探す。
   * 計画強度がある回は、それより古い回のうち計画強度が同じで最も新しい回。計画強度が無い回（期分けプログラム外）は、判定窓より古い計画外の回のうち最も新しい回。 (4)
   * 1件でも比較相手が無ければ判定不可。(5) 3件とも比較相手を上回っていなければ、その指標は停滞。
   */
  static StagnationLevel evaluateSessions(List<Session> recentFirst) {
    if (recentFirst == null) return StagnationLevel.INSUFFICIENT;
    List<Session> sessions = new ArrayList<>();
    for (Session s : recentFirst) {
      if (!s.deload()) sessions.add(s);
    }
    if (sessions.size() <= WINDOW_SIZE) return StagnationLevel.INSUFFICIENT;

    boolean volumeStagnant = true;
    boolean maxWeightStagnant = true;
    for (int i = 0; i < WINDOW_SIZE; i++) {
      Session target = sessions.get(i);
      Session comparison = findComparison(sessions, i);
      if (comparison == null) return StagnationLevel.INSUFFICIENT;
      if (target.totalVolume() > comparison.totalVolume()) volumeStagnant = false;
      if (target.maxWeight() > comparison.maxWeight()) maxWeightStagnant = false;
    }
    if (volumeStagnant && maxWeightStagnant) return StagnationLevel.STRONG;
    if (volumeStagnant) return StagnationLevel.MILD;
    return StagnationLevel.NONE;
  }

  /** 判定窓のindex番目のセッションの比較相手。見つからなければnull。 */
  private static Session findComparison(List<Session> sessions, int index) {
    Session target = sessions.get(index);
    if (target.plannedIntensityPct() == null) {
      // 期分けプログラム外: 判定窓の直前にある計画外のセッション
      for (int j = WINDOW_SIZE; j < sessions.size(); j++) {
        if (sessions.get(j).plannedIntensityPct() == null) return sessions.get(j);
      }
      return null;
    }
    for (int j = index + 1; j < sessions.size(); j++) {
      BigDecimal pct = sessions.get(j).plannedIntensityPct();
      if (pct != null && sameIntensity(target.plannedIntensityPct(), pct)) return sessions.get(j);
    }
    return null;
  }

  static boolean sameIntensity(BigDecimal a, BigDecimal b) {
    return a.subtract(b).abs().compareTo(INTENSITY_MATCH_TOLERANCE_PCT) <= 0;
  }

  private static double value(Double d) {
    return d != null ? d : 0.0;
  }

  /** 日付→その日が属する期分けサイクルの週の計画（強度・ディロード）を引く。 */
  static final class PlanLookup {
    record WeekPlan(BigDecimal intensityPct, boolean deload) {}

    private final List<ProgramCycle> cycles;
    private final Map<Long, Map<Integer, WeekPlan>> weeksByCycle = new HashMap<>();
    private final ProgramCycleDao dao;

    PlanLookup(ProgramCycleDao dao, Long userId, LocalDate from, LocalDate to) {
      this.dao = dao;
      this.cycles = dao.selectOverlappingByUserId(userId, from, to);
    }

    WeekPlan find(LocalDate date) {
      ProgramCycle found = null;
      for (ProgramCycle c : cycles) {
        LocalDate end = c.getStartDate().plusDays(7L * c.getTotalWeeks());
        if (!date.isBefore(c.getStartDate()) && date.isBefore(end)) found = c;
      }
      if (found == null) return null;
      Map<Integer, WeekPlan> weeks =
          weeksByCycle.computeIfAbsent(
              found.getId(),
              id -> {
                Map<Integer, WeekPlan> m = new HashMap<>();
                for (ProgramCycleWeek w : dao.selectWeeksByCycleId(id)) {
                  m.put(
                      w.getWeekNumber(),
                      new WeekPlan(w.getTargetIntensityPct(), Boolean.TRUE.equals(w.getDeload())));
                }
                return m;
              });
      return weeks.get(PeriodizationService.weekNumberOf(found, date));
    }
  }
}
