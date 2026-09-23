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
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 停滞検知（ディロード週の任意提案）。機能見直し-1-#3 詳細設計書3-3節、QA Q3-4（2026-09-19確定、2026-09-24比較基準確定）。
 *
 * <ul>
 *   <li>比較基準は「判定窓（直近3回）の直前のセッション」。窓内のどのセッションも基準の値を上回っていなければ停滞。
 *       指標A（主軸、合計ボリューム）・指標B（補助、最大重量）とも同じ組み立て。
 *   <li>判定には最低4回分（基準1回＋窓3回）の記録が必要。足りなければ判定不可（INSUFFICIENT、提案しない）。
 *   <li>期分けプログラムのディロード週のセッションは判定窓・基準の両方から除外する。 計画強度(%1RM)が基準セッションより低い週のセッションも除外し、さらに過去へさかのぼって集める。
 *       期分けプログラム外のセッションは通常どおり対象。
 *   <li>Aのみ停滞=MILD、AとBの両方が停滞=STRONG。いずれも提案のみで強制はしない。
 *   <li>判定は記録保存時に行って結果を保持する（{@link #evaluateAndStore}）。表示側は保持した結果を読む。
 * </ul>
 *
 * <p>上記は確立した標準ではなく、現有データで実装しやすい合理的な近似（会議録#3の2026-09-23追記）。誤検知が多ければ見直す。
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

  /** 判定に必要な最低回数（基準1回＋窓3回）。 */
  static final int REQUIRED_SESSIONS = WINDOW_SIZE + 1;

  /** 除外対象（ディロード週・低強度週）を飛ばして過去へさかのぼるときに読み込む最大セッション数。 判定ロジック上の閾値ではなく、1回の判定で読むデータ量の上限（性能上の上限）。 */
  static final int MAX_LOOKBACK_SESSIONS = 30;

  public enum StagnationLevel {
    NONE,
    MILD,
    STRONG,
    /** 記録が最低回数に満たず判定できない（提案を出さない）。 */
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
    if (recentFirst.size() < REQUIRED_SESSIONS) return StagnationLevel.INSUFFICIENT;

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
   * <p>手順: (1) ディロード週のセッションを除外する。(2) 先頭3件を判定窓、4件目を基準とする。 (3)
   * 判定窓のうち計画強度が基準より低いセッションを除外し、足りない分は過去から補って(2)に戻る。 (4) 窓内のどのセッションも基準の値を上回っていなければ、その指標は停滞。
   */
  static StagnationLevel evaluateSessions(List<Session> recentFirst) {
    if (recentFirst == null) return StagnationLevel.INSUFFICIENT;
    List<Session> candidates = new ArrayList<>();
    for (Session s : recentFirst) {
      if (!s.deload()) candidates.add(s);
    }
    while (true) {
      if (candidates.size() < REQUIRED_SESSIONS) return StagnationLevel.INSUFFICIENT;
      Session reference = candidates.get(WINDOW_SIZE);
      List<Session> lowerIntensity = new ArrayList<>();
      for (int i = 0; i < WINDOW_SIZE; i++) {
        Session s = candidates.get(i);
        if (isLowerIntensity(s, reference)) lowerIntensity.add(s);
      }
      if (lowerIntensity.isEmpty()) {
        List<Session> window = candidates.subList(0, WINDOW_SIZE);
        boolean volumeStagnant = noneExceeds(window, reference, Session::totalVolume);
        boolean maxWeightStagnant = noneExceeds(window, reference, Session::maxWeight);
        if (volumeStagnant && maxWeightStagnant) return StagnationLevel.STRONG;
        if (volumeStagnant) return StagnationLevel.MILD;
        return StagnationLevel.NONE;
      }
      candidates.removeAll(lowerIntensity);
    }
  }

  /** 両方とも期分けプログラム内で、判定窓側の計画強度が基準より低い場合のみ比較不成立とする。 */
  private static boolean isLowerIntensity(Session s, Session reference) {
    return s.plannedIntensityPct() != null
        && reference.plannedIntensityPct() != null
        && s.plannedIntensityPct().compareTo(reference.plannedIntensityPct()) < 0;
  }

  private static boolean noneExceeds(
      List<Session> window, Session reference, Function<Session, Double> metric) {
    double base = metric.apply(reference);
    for (Session s : window) {
      if (metric.apply(s) > base) return false;
    }
    return true;
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
