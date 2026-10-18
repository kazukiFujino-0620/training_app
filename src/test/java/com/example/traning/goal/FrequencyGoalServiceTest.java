package com.example.traning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.example.traning.periodization.ProgramCycle;
import com.example.traning.periodization.ProgramCycleDao;
import com.example.traning.periodization.ProgramCycleDayTemplate;
import com.example.traning.training.dao.TrainingDao;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 機能見直し-1-#7: 週の回数目標の判定（完了週のみ・4週未満の表示・週ごとの確定目標・プログラムの週）。 */
@ExtendWith(MockitoExtension.class)
class FrequencyGoalServiceTest {

  private static final Long USER = 5L;
  // 2026-09-07 は月曜
  private static final LocalDate W1 = LocalDate.of(2026, 9, 7);

  @Mock private TrainingDao trainingDao;
  @Mock private ProgramCycleDao programCycleDao;

  private InMemoryFrequencyGoalDao dao;
  private FrequencyGoalService service;
  private final List<LocalDate> trainedDates = new ArrayList<>();
  private final List<ProgramCycle> cycles = new ArrayList<>();
  private final List<ProgramCycleDayTemplate> dayTemplates = new ArrayList<>();

  @BeforeEach
  void setUp() {
    dao = new InMemoryFrequencyGoalDao();
    service = new FrequencyGoalService(dao, trainingDao, programCycleDao);
    lenient()
        .when(trainingDao.selectDistinctTrainingDatesByUserIdAndDateRange(eq(USER), any(), any()))
        .thenAnswer(
            inv -> {
              LocalDate from = inv.getArgument(1);
              LocalDate to = inv.getArgument(2);
              return trainedDates.stream()
                  .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                  .distinct()
                  .toList();
            });
    lenient()
        .when(programCycleDao.selectActiveByUserId(USER))
        .thenAnswer(inv -> cycles.stream().filter(c -> "ACTIVE".equals(c.getStatus())).findFirst());
    lenient()
        .when(programCycleDao.selectOverlappingByUserId(eq(USER), any(), any()))
        .thenAnswer(
            inv -> {
              LocalDate from = inv.getArgument(1);
              LocalDate to = inv.getArgument(2);
              return cycles.stream()
                  .filter(
                      c ->
                          !c.getStartDate().isAfter(to)
                              && c.getStartDate().plusDays(7L * c.getTotalWeeks()).isAfter(from))
                  .toList();
            });
    lenient()
        .when(programCycleDao.selectDayTemplatesByCycleId(anyLong()))
        .thenAnswer(
            inv -> {
              Long id = inv.getArgument(0);
              return dayTemplates.stream().filter(d -> d.getCycleId().equals(id)).toList();
            });
  }

  // ===================== 未設定・起点 =====================

  @Test
  void 目標もプログラムも無ければ未設定として返し週の確定もしない() {
    FrequencyGoalSummary s = service.getSummary(USER, W1.plusDays(2));

    assertThat(s.goalSet()).isFalse();
    assertThat(s.manualTarget()).isNull();
    assertThat(dao.weeks).isEmpty();
  }

  @Test
  void 目標を設定した週は評価に含めず来週から達成率を表示しますと出す() {
    LocalDate wed = W1.plusDays(2);
    trainedDates.add(W1); // 月曜に1回
    service.updateGoal(USER, 3, wed);

    FrequencyGoalSummary s = service.getSummary(USER, wed);

    assertThat(s.goalSet()).isTrue();
    assertThat(s.goalLabel()).isEqualTo("週3回");
    assertThat(s.thisWeekLabel()).isEqualTo("今週 1/3回");
    assertThat(s.evaluatedWeeks()).isZero();
    assertThat(s.resultLabel()).isEqualTo("来週から達成率を表示します");
    assertThat(s.percentLabel()).isNull();
    assertThat(s.notes()).isEmpty();
    assertThat(dao.week(W1).getEvaluated()).isFalse();
  }

  @Test
  void 設定した週のうちに選び直した場合は今週の表示も新しい値にそろえる() {
    service.updateGoal(USER, 3, W1.plusDays(1));
    service.updateGoal(USER, 5, W1.plusDays(2));

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusDays(2));

    assertThat(s.thisWeekTarget()).isEqualTo(5);
    assertThat(s.manualTarget()).isEqualTo(5);
  }

  @Test
  void 目標は1から7回の範囲外なら保存しない() {
    assertThatThrownBy(() -> service.updateGoal(USER, 0, W1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateGoal(USER, 8, W1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(dao.goal).isNull();
  }

  // ===================== 完了週のみ・4週未満の分岐 =====================

  @Test
  void 終わった週が1から3週なら分数だけで割合は出さない() {
    service.updateGoal(USER, 2, W1.plusDays(3)); // W1は設定した週
    trainDays(W1.plusWeeks(1), 0, 2); // 2週目: 2日 → 達成
    trainDays(W1.plusWeeks(2), 0); // 3週目: 1日 → 未達

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(3).plusDays(1));

    assertThat(s.evaluatedWeeks()).isEqualTo(2);
    assertThat(s.achievedWeeks()).isEqualTo(1);
    assertThat(s.resultLabel()).isEqualTo("直近2週 1/2週達成");
    assertThat(s.percentLabel()).isNull();
    assertThat(s.achievementPct()).isNull();
    assertThat(s.notes()).containsExactly("4週たつと達成率（%）も表示します");
  }

  @Test
  void 終わった週が4週以上なら直近4週の割合も出し今週の記録は達成率に含めない() {
    service.updateGoal(USER, 3, W1.plusDays(4));
    trainDays(W1.plusWeeks(1), 0, 2, 4); // 達成
    trainDays(W1.plusWeeks(2), 0, 1, 2, 3, 4, 5); // 6日（超過分は加点しない）→ 達成1週
    trainDays(W1.plusWeeks(3), 1, 3); // 未達
    trainDays(W1.plusWeeks(4), 0, 0, 2, 2, 6); // 同じ日の複数種目は1日 → 3日で達成
    trainDays(W1.plusWeeks(5), 0); // 1日 → 未達（直近4週から外れる最古の週は2週目）
    LocalDate today = W1.plusWeeks(6).plusDays(2);
    trainDays(W1.plusWeeks(6), 0, 1, 2); // 今週は3/3でも達成率には含めない

    FrequencyGoalSummary s = service.getSummary(USER, today);

    // 直近4週＝3〜6週目: 達成(3週目), 未達(4週目), 達成(5週目), 未達(6週目)
    assertThat(s.evaluatedWeeks()).isEqualTo(4);
    assertThat(s.achievedWeeks()).isEqualTo(2);
    assertThat(s.resultLabel()).isEqualTo("直近4週間 2/4週達成");
    assertThat(s.percentLabel()).isEqualTo("（50%）");
    assertThat(s.thisWeekLabel()).isEqualTo("今週 3/3回");
    assertThat(s.notes()).isEmpty();
  }

  @Test
  void 今週の進捗は今日までの記録だけを数え先日付の記録は含めない() {
    service.updateGoal(USER, 3, W1);
    trainDays(W1, 0, 5); // 月曜と土曜（先日付で登録）

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusDays(2));

    assertThat(s.thisWeekDoneDays()).isEqualTo(1);
  }

  // ===================== 週ごとの確定目標（スナップショット） =====================

  @Test
  void 週の途中で目標を変えても今週は変更前の目標で判定し翌週から反映する() {
    service.updateGoal(USER, 3, W1.plusDays(1));
    LocalDate midWeek2 = W1.plusWeeks(1).plusDays(3);
    service.updateGoal(USER, 5, midWeek2);

    assertThat(service.getSummary(USER, midWeek2).thisWeekTarget()).isEqualTo(3);
    assertThat(service.getSummary(USER, W1.plusWeeks(2)).thisWeekTarget()).isEqualTo(5);
    assertThat(dao.week(W1.plusWeeks(1)).getTargetCount()).isEqualTo(3);
  }

  @Test
  void 目標を変えても過去の週の達成未達はさかのぼって変わらない() {
    service.updateGoal(USER, 2, W1);
    trainDays(W1.plusWeeks(1), 0, 2); // 目標2で達成
    assertThat(service.getSummary(USER, W1.plusWeeks(2)).achievedWeeks()).isEqualTo(1);

    service.updateGoal(USER, 7, W1.plusWeeks(2).plusDays(1));

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(3));
    assertThat(dao.week(W1.plusWeeks(1)).getTargetCount()).isEqualTo(2);
    assertThat(s.evaluatedWeeks()).isEqualTo(2);
    assertThat(s.achievedWeeks()).isEqualTo(1); // 2週目は目標2のまま達成
  }

  @Test
  void しばらく開かなかった間の週も開いた時点の目標でまとめて確定する() {
    service.updateGoal(USER, 1, W1);
    trainDays(W1.plusWeeks(1), 3);
    trainDays(W1.plusWeeks(3), 3);

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(4));

    assertThat(dao.weeks).hasSize(5);
    assertThat(s.evaluatedWeeks()).isEqualTo(3);
    assertThat(s.achievedWeeks()).isEqualTo(2);
  }

  @Test
  void 別リクエストが先に同じ週を確定していても先の行を正として処理を続ける() {
    service.updateGoal(USER, 3, W1);
    dao.failNextInsertAsDuplicate = true;

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(2));

    assertThat(s.goalSet()).isTrue();
    assertThat(s.thisWeekTarget()).isEqualTo(3);
  }

  // ===================== 期分けプログラムの週 =====================

  @Test
  void プログラムの期間の週はその週の予定日数を目標にし休養日は数えない() {
    service.updateGoal(USER, 2, W1);
    // 2週目の月曜から開始する4週プログラム。月水金に部位あり、日曜は休養日（部位なし）
    ProgramCycle c = cycle(10L, W1.plusWeeks(1), 4);
    for (int wk = 1; wk <= 4; wk++) {
      day(c, wk, "MON", "CHEST");
      day(c, wk, "WED", "BACK");
      day(c, wk, "FRI", "LEG");
      day(c, wk, "SUN", null);
    }

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(1).plusDays(1));

    assertThat(s.thisWeekTarget()).isEqualTo(3);
    assertThat(s.thisWeekFromProgram()).isTrue();
    assertThat(s.programActive()).isTrue();
    assertThat(s.manualTarget()).isEqualTo(2);
    assertThat(s.notes()).containsExactly("プログラムの予定日数を目標にしています");
    assertThat(s.programSheetLabel()).isEqualTo("プログラムの実施中は、その週の予定日数（今週は3回）を目標にしています。");
    assertThat(dao.week(W1.plusWeeks(1)).getProgramCycleId()).isEqualTo(10L);
  }

  @Test
  void デロード週は減算せずその週の予定日数をそのまま目標にする() {
    service.updateGoal(USER, 3, W1);
    ProgramCycle c = cycle(10L, W1.plusWeeks(1), 2);
    day(c, 1, "MON", "CHEST");
    day(c, 1, "TUE", "BACK");
    day(c, 1, "THU", "LEG");
    day(c, 1, "SAT", "ARM");
    day(c, 2, "MON", "CHEST"); // 2週目はデロード週（日数を減らした構成）
    day(c, 2, "THU", "LEG");

    service.getSummary(USER, W1.plusWeeks(2).plusDays(3));

    assertThat(dao.week(W1.plusWeeks(1)).getTargetCount()).isEqualTo(4);
    assertThat(dao.week(W1.plusWeeks(2)).getTargetCount()).isEqualTo(2);
  }

  @Test
  void 開始日が月曜でないプログラムは日付ごとに予定を引いて数える() {
    service.updateGoal(USER, 3, W1);
    // 水曜開始。プログラムの第1週は水〜火、第2週は翌水〜
    ProgramCycle c = cycle(10L, W1.plusWeeks(1).plusDays(2), 2);
    day(c, 1, "WED", "CHEST");
    day(c, 1, "FRI", "BACK");
    day(c, 1, "MON", "LEG"); // 第1週の月曜＝カレンダー3週目の月曜
    day(c, 2, "WED", "CHEST");
    day(c, 2, "FRI", null);

    // カレンダー3週目（月〜日）: 月=第1週MON(予定), 水=第2週WED(予定), 金=第2週FRI(休養)
    int days =
        FrequencyGoalService.scheduledDays(
            new FrequencyGoalService.CycleWithDays(
                c, java.util.Set.of("1:WED", "1:FRI", "1:MON", "2:WED")),
            W1.plusWeeks(2));
    assertThat(days).isEqualTo(2);
  }

  @Test
  void 週の途中で始めたプログラムは今週の目標を変えず翌週から使う() {
    service.updateGoal(USER, 2, W1);
    LocalDate wed2 = W1.plusWeeks(1).plusDays(2);
    service.getSummary(USER, W1.plusWeeks(1)); // 2週目を手入力の目標で確定済み
    // PeriodizationServiceはサイクル作成の直前にfreezeWeekTargetsを呼ぶ
    service.freezeWeekTargets(USER, wed2);
    ProgramCycle c = cycle(10L, wed2, 4);
    for (int wk = 1; wk <= 4; wk++) {
      day(c, wk, "WED", "CHEST");
      day(c, wk, "THU", "BACK");
      day(c, wk, "SAT", "LEG");
      day(c, wk, "SUN", "ARM");
    }

    FrequencyGoalSummary thisWeek = service.getSummary(USER, wed2);
    assertThat(thisWeek.thisWeekTarget()).isEqualTo(2);
    assertThat(thisWeek.thisWeekFromProgram()).isFalse();
    assertThat(thisWeek.programActive()).isTrue();
    assertThat(thisWeek.programSheetLabel()).isEqualTo("来週から、その週の予定日数を目標にします。");

    FrequencyGoalSummary nextWeek = service.getSummary(USER, W1.plusWeeks(2));
    assertThat(nextWeek.thisWeekTarget()).isEqualTo(4);
    assertThat(nextWeek.thisWeekFromProgram()).isTrue();
  }

  @Test
  void プログラム実施中に手入力の目標を変えてもプログラムの週の目標は変わらない() {
    service.updateGoal(USER, 2, W1);
    ProgramCycle c = cycle(10L, W1.plusWeeks(1), 2);
    day(c, 1, "MON", "CHEST");
    day(c, 1, "WED", "BACK");
    day(c, 2, "MON", "CHEST");
    day(c, 2, "WED", "BACK");
    LocalDate tue = W1.plusWeeks(1).plusDays(1);
    service.getSummary(USER, tue);

    service.updateGoal(USER, 6, tue);

    assertThat(service.getSummary(USER, tue).thisWeekTarget()).isEqualTo(2);
    assertThat(service.getSummary(USER, W1.plusWeeks(2)).thisWeekTarget()).isEqualTo(2);
    assertThat(dao.week(W1.plusWeeks(2)).getSource()).isEqualTo("PROGRAM");
    // プログラム終了後の週から手入力の目標を使う
    assertThat(service.getSummary(USER, W1.plusWeeks(3)).thisWeekTarget()).isEqualTo(6);
    assertThat(dao.week(W1.plusWeeks(3)).getSource()).isEqualTo("MANUAL");
  }

  @Test
  void 手入力の目標が無いプログラム利用者はプログラムの開始週を起点にする() {
    ProgramCycle c = cycle(10L, W1.plusDays(2), 4);
    for (int wk = 1; wk <= 4; wk++) {
      day(c, wk, "MON", "CHEST");
      day(c, wk, "WED", "BACK");
      day(c, wk, "FRI", "LEG");
    }

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(2).plusDays(1));

    assertThat(s.goalSet()).isTrue();
    assertThat(s.manualTarget()).isNull();
    assertThat(dao.week(W1).getEvaluated()).isFalse();
    assertThat(s.evaluatedWeeks()).isEqualTo(1);
  }

  @Test
  void 手入力の目標が無くプログラムも終わった週は未設定の案内に戻る() {
    ProgramCycle c = cycle(10L, W1, 1);
    day(c, 1, "MON", "CHEST");
    service.getSummary(USER, W1);
    c.setStatus("COMPLETED");

    FrequencyGoalSummary s = service.getSummary(USER, W1.plusWeeks(1));

    assertThat(s.goalSet()).isFalse();
    assertThat(dao.week(W1.plusWeeks(1)).getSource()).isEqualTo("NONE");
  }

  // ===================== helpers =====================

  private void trainDays(LocalDate monday, int... offsets) {
    for (int o : offsets) trainedDates.add(monday.plusDays(o));
  }

  private ProgramCycle cycle(Long id, LocalDate start, int totalWeeks) {
    ProgramCycle c = new ProgramCycle();
    c.setId(id);
    c.setUserId(USER);
    c.setStartDate(start);
    c.setTotalWeeks(totalWeeks);
    c.setStatus("ACTIVE");
    cycles.add(c);
    return c;
  }

  private void day(ProgramCycle c, int week, String dow, String part) {
    ProgramCycleDayTemplate d = new ProgramCycleDayTemplate();
    d.setCycleId(c.getId());
    d.setWeekNumber(week);
    d.setDayOfWeek(dow);
    d.setPartCode(part);
    dayTemplates.add(d);
  }

  /** FrequencyGoalDaoのインメモリ実装（SQLファイルと同じ条件で絞り込む）。 */
  static class InMemoryFrequencyGoalDao implements FrequencyGoalDao {
    UserFrequencyGoal goal;
    final List<UserFrequencyWeekTarget> weeks = new ArrayList<>();

    /** 次のinsertWeekTargetを、別リクエストが同じ週を先に確定した状況として扱う（行は入れて一意制約違反を投げる）。 */
    boolean failNextInsertAsDuplicate;

    private final AtomicLong ids = new AtomicLong(1);

    UserFrequencyWeekTarget week(LocalDate monday) {
      return weeks.stream()
          .filter(w -> w.getWeekStartDate().equals(monday))
          .findFirst()
          .orElseThrow();
    }

    @Override
    public Optional<UserFrequencyGoal> selectGoalByUserId(Long userId) {
      return Optional.ofNullable(goal);
    }

    @Override
    public int insertGoal(UserFrequencyGoal entity) {
      entity.setId(ids.getAndIncrement());
      goal = entity;
      return 1;
    }

    @Override
    public int updateGoalCount(Long userId, int weeklyTargetCount) {
      goal.setWeeklyTargetCount(weeklyTargetCount);
      return 1;
    }

    @Override
    public List<UserFrequencyWeekTarget> selectWeekTargetsUntil(
        Long userId, LocalDate weekStartDate, int limit) {
      return weeks.stream()
          .filter(w -> !w.getWeekStartDate().isAfter(weekStartDate))
          .sorted(Comparator.comparing(UserFrequencyWeekTarget::getWeekStartDate).reversed())
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<UserFrequencyWeekTarget> selectLatestWeekTarget(Long userId) {
      return weeks.stream().max(Comparator.comparing(UserFrequencyWeekTarget::getWeekStartDate));
    }

    @Override
    public int insertWeekTarget(UserFrequencyWeekTarget entity) {
      if (weeks.stream().anyMatch(w -> w.getWeekStartDate().equals(entity.getWeekStartDate()))) {
        throw new IllegalStateException(
            "uq_user_frequency_week violated: " + entity.getWeekStartDate());
      }
      entity.setId(ids.getAndIncrement());
      weeks.add(entity);
      if (failNextInsertAsDuplicate) {
        failNextInsertAsDuplicate = false;
        throw org.mockito.Mockito.mock(org.seasar.doma.jdbc.UniqueConstraintException.class);
      }
      return 1;
    }

    @Override
    public int updateUnevaluatedWeekTarget(Long userId, LocalDate weekStartDate, int targetCount) {
      int n = 0;
      for (UserFrequencyWeekTarget w : weeks) {
        if (w.getWeekStartDate().equals(weekStartDate)
            && Boolean.FALSE.equals(w.getEvaluated())
            && "MANUAL".equals(w.getSource())) {
          w.setTargetCount(targetCount);
          n++;
        }
      }
      return n;
    }
  }
}
