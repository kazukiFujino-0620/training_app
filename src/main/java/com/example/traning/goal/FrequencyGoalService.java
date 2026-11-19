package com.example.traning.goal;

import com.example.traning.periodization.CycleStatus;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.periodization.ProgramCycle;
import com.example.traning.periodization.ProgramCycleDao;
import com.example.traning.periodization.ProgramCycleDayTemplate;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.UserTrainingDate;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 週の回数目標と達成率（機能見直し-1-#7 詳細設計書2章・5章）。Web（MenuController・FrequencyGoalApiController）・
 * モバイル（MobileFrequencyGoalController）共通。
 *
 * <p>週は月曜起点。実施日数はtrainingsのdistinct training_date（削除済みを除く。同じ日の複数種目は1日）。
 *
 * <p>週ごとの目標は user_frequency_week_targets に「その週の開始時点の目標」として確定・保存し、一度書いた週は書き換えない （QA
 * Q7-7）。確定は参照時にまとめて行い（定期ジョブは使わない）、目標の変更・期分けプログラムの開始/切替の直前にも {@link
 * #freezeWeekTargets}で今週分までを確定させる。これにより「週の途中の変更は翌週から反映」と 「過去の週の判定がさかのぼって変わらない」を両立する。
 */
@Service
@RequiredArgsConstructor
public class FrequencyGoalService {

  public static final int MIN_TARGET = 1;
  public static final int MAX_TARGET = 7;

  /** 達成率の計算で見る確定目標の行数（目標なしの週・設定した週を飛ばすため多めに取る）。 */
  static final int WEEK_TARGET_LOOKBACK = 60;

  /** 担当会員の一覧（機能見直し-1-#12）で返す、直近の終わった週の数。 */
  public static final int LAST_COMPLETED_WEEKS = 2;

  private final FrequencyGoalDao frequencyGoalDao;
  private final TrainingDao trainingDao;
  private final ProgramCycleDao programCycleDao;

  /** カード・設定シートの表示データ。未確定の週があればここで確定させるため読み取り専用にしない。 */
  @Transactional
  public FrequencyGoalSummary getSummary(Long userId, LocalDate today) {
    frequencyGoalDao.lockUserForWeekTargets(userId);
    Optional<UserFrequencyGoal> goal = frequencyGoalDao.selectGoalByUserId(userId);
    ensureWeekTargets(userId, today, goal);

    LocalDate thisMonday = weekStartOf(today);
    Integer manualTarget = goal.map(UserFrequencyGoal::getWeeklyTargetCount).orElse(null);
    boolean programActive = activeCycleOn(userId, today).isPresent();

    // 今週1行＋評価対象の過去週。目標なしの週・設定した週を飛ばすため多めに取る
    List<UserFrequencyWeekTarget> rows =
        frequencyGoalDao.selectWeekTargetsUntil(userId, thisMonday, WEEK_TARGET_LOOKBACK);
    if (!hasThisWeekTarget(rows, thisMonday)) {
      return summarize(rows, Set.of(), today, manualTarget, programActive);
    }
    LocalDate from = trainedDatesFrom(rows, thisMonday);
    Set<LocalDate> trainedDates =
        new HashSet<>(
            trainingDao.selectDistinctTrainingDatesByUserIdAndDateRange(userId, from, today));
    return summarize(rows, trainedDates, today, manualTarget, programActive);
  }

  private static boolean hasThisWeekTarget(
      List<UserFrequencyWeekTarget> rows, LocalDate thisMonday) {
    return rows.stream()
        .anyMatch(
            r ->
                r.getWeekStartDate().equals(thisMonday)
                    && !WeekTargetSource.NONE.name().equals(r.getSource()));
  }

  /**
   * スタッフ向けの担当会員の一覧（機能見直し-1-#12）用に、複数の会員の表示データと直近の終わった週の結果をまとめて求める。 {@link
   * #getSummary}と同じ計算で、会員ごとのSQLは未確定の週がある会員の確定処理だけにする。
   *
   * @return 会員IDごとの状況（全員分。目標が無い会員は{@code summary.goalSet()=false}）
   */
  @Transactional
  public Map<Long, MemberGoalStatus> getStatusesForMembers(
      Collection<Long> memberIds, LocalDate today) {
    if (memberIds.isEmpty()) return Map.of();
    List<Long> ids = memberIds.stream().distinct().sorted().toList();
    LocalDate thisMonday = weekStartOf(today);
    LocalDate windowFrom = thisMonday.minusWeeks(WEEK_TARGET_LOOKBACK - 1L);

    Map<Long, Integer> manualTargets = new HashMap<>();
    for (UserFrequencyGoal g : frequencyGoalDao.selectGoalsByUserIds(ids)) {
      manualTargets.put(g.getUserId(), g.getWeeklyTargetCount());
    }
    Set<Long> programActive = new HashSet<>();
    for (ProgramCycle c : programCycleDao.selectActiveByUserIds(ids)) {
      if (!c.getStartDate().isAfter(today) && withinCycle(c, today)) {
        programActive.add(c.getUserId());
      }
    }
    Map<Long, List<UserFrequencyWeekTarget>> rowsByUser =
        groupByUser(
            frequencyGoalDao.selectWeekTargetsByUserIdsBetween(ids, windowFrom, thisMonday));

    // 今週分まで確定していない会員だけ、getSummaryと同じ確定処理を行う（ID順にロックしてデッドロックを避ける）
    List<Long> stale = new ArrayList<>();
    for (Long id : ids) {
      List<UserFrequencyWeekTarget> rows = rowsByUser.getOrDefault(id, List.of());
      boolean tracked = !rows.isEmpty();
      boolean current = tracked && !rows.get(0).getWeekStartDate().isBefore(thisMonday);
      if (!current && (tracked || manualTargets.containsKey(id) || programActive.contains(id))) {
        stale.add(id);
      }
    }
    for (Long id : stale) {
      frequencyGoalDao.lockUserForWeekTargets(id);
      ensureWeekTargets(id, today, frequencyGoalDao.selectGoalByUserId(id));
    }
    if (!stale.isEmpty()) {
      rowsByUser.putAll(
          groupByUser(
              frequencyGoalDao.selectWeekTargetsByUserIdsBetween(stale, windowFrom, thisMonday)));
    }

    LocalDate from = thisMonday.minusWeeks(LAST_COMPLETED_WEEKS);
    for (List<UserFrequencyWeekTarget> rows : rowsByUser.values()) {
      LocalDate f = trainedDatesFrom(rows, thisMonday);
      if (f.isBefore(from)) from = f;
    }
    Map<Long, Set<LocalDate>> datesByUser = new HashMap<>();
    for (UserTrainingDate d :
        trainingDao.selectDistinctTrainingDatesByUserIdsAndDateRange(ids, from, today)) {
      datesByUser.computeIfAbsent(d.getUserId(), k -> new HashSet<>()).add(d.getTrainingDate());
    }

    Map<Long, MemberGoalStatus> result = new HashMap<>();
    for (Long id : ids) {
      List<UserFrequencyWeekTarget> rows = rowsByUser.getOrDefault(id, List.of());
      Set<LocalDate> dates = datesByUser.getOrDefault(id, Set.of());
      FrequencyGoalSummary summary =
          summarize(rows, dates, today, manualTargets.get(id), programActive.contains(id));
      List<WeekResult> lastWeeks = new ArrayList<>();
      for (int i = LAST_COMPLETED_WEEKS; i >= 1; i--) {
        LocalDate weekStart = thisMonday.minusWeeks(i);
        rows.stream()
            .filter(r -> r.getWeekStartDate().equals(weekStart))
            .findFirst()
            .ifPresent(
                r ->
                    lastWeeks.add(
                        new WeekResult(
                            weekStart,
                            r.getTargetCount() == null ? 0 : r.getTargetCount(),
                            countDays(dates, weekStart, weekStart.plusDays(6)),
                            Boolean.TRUE.equals(r.getEvaluated()))));
      }
      result.put(id, new MemberGoalStatus(summary, lastWeeks));
    }
    return result;
  }

  /** 確定目標の行（新しい週から順）と実施日から表示データを作る。getSummaryと担当会員の一覧で共通。 */
  static FrequencyGoalSummary summarize(
      List<UserFrequencyWeekTarget> rows,
      Set<LocalDate> trainedDates,
      LocalDate today,
      Integer manualTarget,
      boolean programActive) {
    LocalDate thisMonday = weekStartOf(today);
    UserFrequencyWeekTarget thisWeek =
        rows.stream().filter(r -> r.getWeekStartDate().equals(thisMonday)).findFirst().orElse(null);
    if (thisWeek == null || (WeekTargetSource.NONE.name().equals(thisWeek.getSource()))) {
      return new FrequencyGoalSummary(false, null, 0, false, 0, 0, manualTarget, programActive);
    }

    List<UserFrequencyWeekTarget> evaluated = evaluatedWeeks(rows, thisMonday);
    int achieved = 0;
    for (UserFrequencyWeekTarget w : evaluated) {
      if (isAchieved(trainedDates, w.getWeekStartDate(), w.getTargetCount())) achieved++;
    }
    // 先日付で登録した記録は今週の進捗に含めない（今日まで）
    int thisWeekDone = countDays(trainedDates, thisMonday, today);

    return new FrequencyGoalSummary(
        true,
        thisWeek.getTargetCount(),
        thisWeekDone,
        WeekTargetSource.PROGRAM.name().equals(thisWeek.getSource()),
        evaluated.size(),
        achieved,
        manualTarget,
        programActive);
  }

  /** 達成率の計算に使う終わった週（新しい週から最大{@link FrequencyGoalSummary#EVALUATION_WEEKS}週）。 */
  private static List<UserFrequencyWeekTarget> evaluatedWeeks(
      List<UserFrequencyWeekTarget> rows, LocalDate thisMonday) {
    return rows.stream()
        .filter(r -> r.getWeekStartDate().isBefore(thisMonday))
        .filter(r -> Boolean.TRUE.equals(r.getEvaluated()))
        .filter(r -> r.getTargetCount() != null && r.getTargetCount() > 0)
        .limit(FrequencyGoalSummary.EVALUATION_WEEKS)
        .toList();
  }

  /** 達成率の計算に実施日が必要な最初の日。 */
  private static LocalDate trainedDatesFrom(
      List<UserFrequencyWeekTarget> rows, LocalDate thisMonday) {
    List<UserFrequencyWeekTarget> evaluated = evaluatedWeeks(rows, thisMonday);
    return evaluated.isEmpty()
        ? thisMonday
        : evaluated.get(evaluated.size() - 1).getWeekStartDate();
  }

  private static Map<Long, List<UserFrequencyWeekTarget>> groupByUser(
      List<UserFrequencyWeekTarget> rows) {
    Map<Long, List<UserFrequencyWeekTarget>> map = new HashMap<>();
    for (UserFrequencyWeekTarget r : rows) {
      map.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
    }
    // 新しい週から順（selectWeekTargetsUntilと同じ並び）
    map.values()
        .forEach(
            l ->
                l.sort(Comparator.comparing(UserFrequencyWeekTarget::getWeekStartDate).reversed()));
    return map;
  }

  /**
   * 手入力の目標を保存する。変更は翌週から反映し、今週は変更前の目標で判定する（設計書5-2節）。
   * 期分けプログラムの実施中でも保存はでき、プログラム対象外の週から使われる（プログラムの週の目標は変わらない）。
   */
  @Transactional
  public void updateGoal(Long userId, int weeklyTargetCount, LocalDate today) {
    if (weeklyTargetCount < MIN_TARGET || weeklyTargetCount > MAX_TARGET) {
      throw new IllegalArgumentException(
          "週の回数目標は" + MIN_TARGET + "〜" + MAX_TARGET + "回の範囲で指定してください");
    }
    frequencyGoalDao.lockUserForWeekTargets(userId);
    Optional<UserFrequencyGoal> existing = frequencyGoalDao.selectGoalByUserId(userId);
    // 変更前の目標で今週分までを確定させてから書き換える
    ensureWeekTargets(userId, today, existing);
    if (existing.isPresent()) {
      frequencyGoalDao.updateGoalCount(userId, weeklyTargetCount);
    } else {
      UserFrequencyGoal g = new UserFrequencyGoal();
      g.setUserId(userId);
      g.setWeeklyTargetCount(weeklyTargetCount);
      frequencyGoalDao.insertGoal(g);
    }
    // 目標を設定した週（評価に含めない週）の中での選び直しは、表示を新しい値にそろえる
    frequencyGoalDao.updateUnevaluatedWeekTarget(userId, weekStartOf(today), weeklyTargetCount);
    // 初めての設定なら、ここで「設定した週」を起点として確定する
    ensureWeekTargets(userId, today, frequencyGoalDao.selectGoalByUserId(userId));
  }

  /** 期分けプログラムの開始・切替の直前に呼ぶ（PeriodizationService）。今週分までを現在の状態で確定させる。 */
  @Transactional
  public void freezeWeekTargets(Long userId, LocalDate today) {
    frequencyGoalDao.lockUserForWeekTargets(userId);
    ensureWeekTargets(userId, today, frequencyGoalDao.selectGoalByUserId(userId));
  }

  // ===================== 週ごとの確定目標 =====================

  /**
   * まだ確定していない週（最後に確定した週の翌週〜今週）を、現在の目標・プログラムの状態で確定する。 確定済みの週が1つも無い場合は、追跡を始めた週（目標を設定した週、または
   * 実施中プログラムの開始週）を評価に含めない週として確定し、そこから今週までを埋める。
   */
  void ensureWeekTargets(Long userId, LocalDate today, Optional<UserFrequencyGoal> goal) {
    LocalDate thisMonday = weekStartOf(today);
    Optional<UserFrequencyWeekTarget> latest = frequencyGoalDao.selectLatestWeekTarget(userId);
    LocalDate firstWeek;
    boolean firstIsStart;
    if (latest.isPresent()) {
      if (!latest.get().getWeekStartDate().isBefore(thisMonday)) return;
      firstWeek = latest.get().getWeekStartDate().plusWeeks(1);
      firstIsStart = false;
    } else {
      Optional<LocalDate> start = trackingStartWeek(userId, today, goal);
      if (start.isEmpty()) return;
      firstWeek = start.get();
      firstIsStart = true;
    }

    List<CycleWithDays> cycles = programCyclesBetween(userId, firstWeek, thisMonday.plusDays(6));
    for (LocalDate w = firstWeek; !w.isAfter(thisMonday); w = w.plusWeeks(1)) {
      WeekTarget t = targetForWeek(w, goal.map(UserFrequencyGoal::getWeeklyTargetCount), cycles);
      UserFrequencyWeekTarget row = new UserFrequencyWeekTarget();
      row.setUserId(userId);
      row.setWeekStartDate(w);
      row.setTargetCount(t.count());
      row.setSource(t.source().name());
      row.setProgramCycleId(t.programCycleId());
      row.setEvaluated(!(firstIsStart && w.equals(firstWeek)));
      try {
        frequencyGoalDao.insertWeekTarget(row);
      } catch (DuplicateKeyException | UniqueConstraintException e) {
        // 同じユーザーの別リクエスト（Webとモバイルの同時表示など）が先に同じ週を確定した。先に確定した行を正とし、書き換えない。
        // Spring管理のDAOはDomaの一意制約違反をDuplicateKeyExceptionに変換して投げる（DomaPersistenceExceptionTranslator）。
        // DAOは@Transactionalを持たないためrollback-onlyにはならず、MySQLでも失敗した文だけが取り消されてトランザクションは続行できる
      }
    }
  }

  /** 追跡の起点（評価に含めない最初の週）。手入力の目標を設定した週と、実施中プログラムの開始週の早いほう。 */
  private Optional<LocalDate> trackingStartWeek(
      Long userId, LocalDate today, Optional<UserFrequencyGoal> goal) {
    LocalDate thisMonday = weekStartOf(today);
    // 手入力の目標は保存と同時に（updateGoal内で）確定させるため、ここに来るのは保存した週。
    // created_atはDBのタイムゾーンに依存するため使わない
    Optional<LocalDate> fromGoal = goal.map(g -> thisMonday);
    Optional<LocalDate> fromProgram =
        activeCycleOn(userId, today).map(c -> weekStartOf(c.getStartDate()));
    return java.util.stream.Stream.of(fromGoal, fromProgram)
        .flatMap(Optional::stream)
        .map(d -> d.isAfter(thisMonday) ? thisMonday : d)
        .min(Comparator.naturalOrder());
  }

  /** ある週の目標（設計書5-2節）。期分けプログラムの期間に掛かる週はプログラムの予定日数（デロード週も減算せずそのまま）、 それ以外の週は手入力の目標。どちらも無ければ目標なし。 */
  static WeekTarget targetForWeek(
      LocalDate weekStart, Optional<Integer> manualTarget, List<CycleWithDays> cycles) {
    LocalDate weekEnd = weekStart.plusDays(6);
    Optional<CycleWithDays> cycle =
        cycles.stream()
            .filter(c -> overlaps(c.cycle(), weekStart, weekEnd))
            .max(Comparator.comparing(c -> c.cycle().getId()));
    if (cycle.isPresent()) {
      return new WeekTarget(
          scheduledDays(cycle.get(), weekStart),
          WeekTargetSource.PROGRAM,
          cycle.get().cycle().getId());
    }
    return manualTarget
        .map(n -> new WeekTarget(n, WeekTargetSource.MANUAL, null))
        .orElse(new WeekTarget(0, WeekTargetSource.NONE, null));
  }

  /**
   * 月〜日の7日間のうち、プログラム上トレーニングの予定がある日数。プログラムの「第N週」は開始日から数えた7日区切りで
   * 月曜起点の週とずれるため、日付ごとに第N週・曜日を求めて日別テンプレート（部位あり＝予定日。部位なしは休養日）を引く。 プログラムどおりにこなせば必ず達成になる数え方。
   */
  static int scheduledDays(CycleWithDays c, LocalDate weekStart) {
    int count = 0;
    for (int i = 0; i < 7; i++) {
      LocalDate d = weekStart.plusDays(i);
      if (!withinCycle(c.cycle(), d)) continue;
      String key =
          PeriodizationService.weekNumberOf(c.cycle(), d)
              + ":"
              + PeriodizationService.toDayCode(d.getDayOfWeek());
      if (c.trainingDayKeys().contains(key)) count++;
    }
    return count;
  }

  private List<CycleWithDays> programCyclesBetween(Long userId, LocalDate from, LocalDate to) {
    // 新しいプログラムに切り替えて退避（ARCHIVED）されたサイクルは、切替の直前に確定済みのため対象外
    return programCycleDao.selectOverlappingByUserId(userId, from, to).stream()
        .filter(
            c ->
                CycleStatus.ACTIVE.name().equals(c.getStatus())
                    || CycleStatus.COMPLETED.name().equals(c.getStatus()))
        .map(c -> new CycleWithDays(c, trainingDayKeys(c.getId())))
        .toList();
  }

  private Set<String> trainingDayKeys(Long cycleId) {
    Set<String> keys = new HashSet<>();
    for (ProgramCycleDayTemplate d : programCycleDao.selectDayTemplatesByCycleId(cycleId)) {
      if (d.getPartCode() != null && !d.getPartCode().isBlank()) {
        keys.add(d.getWeekNumber() + ":" + d.getDayOfWeek());
      }
    }
    return keys;
  }

  /** 今日がプログラムの期間内にあるACTIVEなサイクル。 */
  private Optional<ProgramCycle> activeCycleOn(Long userId, LocalDate today) {
    return programCycleDao
        .selectActiveByUserId(userId)
        .filter(c -> !c.getStartDate().isAfter(today) && withinCycle(c, today));
  }

  private static boolean withinCycle(ProgramCycle c, LocalDate d) {
    LocalDate end = c.getStartDate().plusDays(7L * c.getTotalWeeks()); // 終了日の翌日
    return !d.isBefore(c.getStartDate()) && d.isBefore(end);
  }

  private static boolean overlaps(ProgramCycle c, LocalDate from, LocalDate to) {
    LocalDate end = c.getStartDate().plusDays(7L * c.getTotalWeeks());
    return !c.getStartDate().isAfter(to) && end.isAfter(from);
  }

  private static boolean isAchieved(Set<LocalDate> dates, LocalDate weekStart, int target) {
    // 目標を超えた分は加点しない（達成1週として数えるだけ）
    return countDays(dates, weekStart, weekStart.plusDays(6)) >= target;
  }

  private static int countDays(Set<LocalDate> dates, LocalDate from, LocalDate to) {
    int n = 0;
    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
      if (dates.contains(d)) n++;
    }
    return n;
  }

  public static LocalDate weekStartOf(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /**
   * 担当会員の一覧（機能見直し-1-#12）用の会員ごとの状況。
   *
   * @param lastCompletedWeeks 直近の終わった週（古い週から順。確定目標の行が無い週は含まない）
   */
  public record MemberGoalStatus(
      FrequencyGoalSummary summary, List<WeekResult> lastCompletedWeeks) {}

  /**
   * 終わった週1週分の結果。
   *
   * @param target その週の確定目標（0は目標なし）
   * @param doneDays 実施日数
   * @param evaluated 達成率の計算に含める週か（目標を設定した週はfalse）
   */
  public record WeekResult(LocalDate weekStart, int target, int doneDays, boolean evaluated) {}

  record WeekTarget(int count, WeekTargetSource source, Long programCycleId) {}

  /** サイクルと、予定日（部位あり）の「第N週:曜日」キーの集合。 */
  record CycleWithDays(ProgramCycle cycle, Set<String> trainingDayKeys) {}
}
