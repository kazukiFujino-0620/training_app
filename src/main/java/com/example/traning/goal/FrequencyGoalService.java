package com.example.traning.goal;

import com.example.traning.periodization.CycleStatus;
import com.example.traning.periodization.PeriodizationService;
import com.example.traning.periodization.ProgramCycle;
import com.example.traning.periodization.ProgramCycleDao;
import com.example.traning.periodization.ProgramCycleDayTemplate;
import com.example.traning.training.dao.TrainingDao;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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

  private final FrequencyGoalDao frequencyGoalDao;
  private final TrainingDao trainingDao;
  private final ProgramCycleDao programCycleDao;

  /** カード・設定シートの表示データ。未確定の週があればここで確定させるため読み取り専用にしない。 */
  @Transactional
  public FrequencyGoalSummary getSummary(Long userId, LocalDate today) {
    Optional<UserFrequencyGoal> goal = frequencyGoalDao.selectGoalByUserId(userId);
    ensureWeekTargets(userId, today, goal);

    LocalDate thisMonday = weekStartOf(today);
    Integer manualTarget = goal.map(UserFrequencyGoal::getWeeklyTargetCount).orElse(null);
    boolean programActive = activeCycleOn(userId, today).isPresent();

    // 今週1行＋評価対象の過去週。目標なしの週・設定した週を飛ばすため多めに取る
    List<UserFrequencyWeekTarget> rows =
        frequencyGoalDao.selectWeekTargetsUntil(userId, thisMonday, 60);
    UserFrequencyWeekTarget thisWeek =
        rows.stream().filter(r -> r.getWeekStartDate().equals(thisMonday)).findFirst().orElse(null);
    if (thisWeek == null || (WeekTargetSource.NONE.name().equals(thisWeek.getSource()))) {
      return new FrequencyGoalSummary(false, null, 0, false, 0, 0, manualTarget, programActive);
    }

    List<UserFrequencyWeekTarget> evaluated =
        rows.stream()
            .filter(r -> r.getWeekStartDate().isBefore(thisMonday))
            .filter(r -> Boolean.TRUE.equals(r.getEvaluated()))
            .filter(r -> r.getTargetCount() != null && r.getTargetCount() > 0)
            .limit(FrequencyGoalSummary.EVALUATION_WEEKS)
            .toList();

    LocalDate from =
        evaluated.isEmpty() ? thisMonday : evaluated.get(evaluated.size() - 1).getWeekStartDate();
    Set<LocalDate> trainedDates =
        new HashSet<>(
            trainingDao.selectDistinctTrainingDatesByUserIdAndDateRange(userId, from, today));

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
      frequencyGoalDao.insertWeekTarget(row);
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

  record WeekTarget(int count, WeekTargetSource source, Long programCycleId) {}

  /** サイクルと、予定日（部位あり）の「第N週:曜日」キーの集合。 */
  record CycleWithDays(ProgramCycle cycle, Set<String> trainingDayKeys) {}
}
