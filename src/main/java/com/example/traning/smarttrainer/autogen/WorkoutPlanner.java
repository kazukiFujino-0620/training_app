package com.example.traning.smarttrainer.autogen;

import com.example.traning.restpreference.RestIntervalCalculationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * メニュー自動作成の組み立て（機能見直し-1-#4 詳細設計書5-2節）。DBに触れない純粋な計算のみ。
 *
 * <p>手順: 部位ごとに種目を選ぶ → 部位・セッションの上限に収める → 使える時間に収まるまで決まった順番で削る。
 */
public final class WorkoutPlanner {

  private WorkoutPlanner() {}

  /** 生成候補の種目。候補の並び＝その部位での選びやすさの順（サービス側で決める）。 */
  public record Candidate(
      String itemName,
      String partCode,
      boolean compound,
      boolean bodyweight,
      double weightIncrementKg,
      Double oneRm,
      Double lastMainWeight,
      Integer restOverrideSeconds) {}

  /**
   * 対象部位。リストの並び＝優先度（先頭が高い）。
   *
   * @param specified ユーザーまたはプログラムが指定した部位か（指定の無い部位から先に削る）
   * @param fatigued 高疲労か（指定部位では注意文を出すのみ）
   * @param programItemCount candidates の先頭からこの件数がプログラムの当日の種目（器具・除外の条件に合うもの、登録順）
   */
  public record PartRequest(
      String partCode,
      boolean specified,
      boolean fatigued,
      List<Candidate> candidates,
      int programItemCount) {

    public PartRequest(
        String partCode, boolean specified, boolean fatigued, List<Candidate> candidates) {
      this(partCode, specified, fatigued, candidates, 0);
    }
  }

  /**
   * @param intensity 推定1RMに対する割合（0.0〜1.0）
   * @param reps 回数
   * @param baseSets 1種目あたりのセット数（目的別戦略の値）
   * @param setReduction 全部位が高疲労のときに減らすセット数
   */
  public record PlanInput(
      int availableMinutes,
      List<PartRequest> parts,
      double intensity,
      int reps,
      int baseSets,
      int setReduction) {}

  public enum WeightSource {
    ESTIMATED_1RM,
    LAST_MAIN_SET,
    BODYWEIGHT,
    NONE
  }

  public record WeightSuggestion(Double weightKg, WeightSource source) {}

  public record Alternative(Candidate candidate, WeightSuggestion weight, int restSeconds) {}

  public enum Status {
    OK,
    NO_CANDIDATES,
    TOO_SHORT
  }

  public static final class PlannedItem {
    private final Candidate candidate;
    private final PartRequest part;
    private final int partPriority;
    private final int restSeconds;
    private final WeightSuggestion weight;
    private int sets;
    private List<Alternative> alternatives = List.of();

    PlannedItem(
        Candidate candidate,
        PartRequest part,
        int partPriority,
        int sets,
        int restSeconds,
        WeightSuggestion weight) {
      this.candidate = candidate;
      this.part = part;
      this.partPriority = partPriority;
      this.sets = sets;
      this.restSeconds = restSeconds;
      this.weight = weight;
    }

    public Candidate candidate() {
      return candidate;
    }

    public PartRequest part() {
      return part;
    }

    public int sets() {
      return sets;
    }

    public int restSeconds() {
      return restSeconds;
    }

    public WeightSuggestion weight() {
      return weight;
    }

    public List<Alternative> alternatives() {
      return alternatives;
    }
  }

  public record PlanResult(Status status, List<PlannedItem> items, int estimatedSeconds) {}

  /** 1つの部位の組み立て途中の状態。 */
  private static final class PartPlan {
    final PartRequest request;
    final int priority;
    final List<PlannedItem> items = new ArrayList<>();

    PartPlan(PartRequest request, int priority) {
      this.request = request;
      this.priority = priority;
    }

    int totalSets() {
      return items.stream().mapToInt(i -> i.sets).sum();
    }
  }

  // ===================== 入口 =====================

  public static PlanResult plan(PlanInput in) {
    int setsPerItem =
        Math.max(AutogenRules.MIN_SETS_PER_ITEM, in.baseSets() - Math.max(0, in.setReduction()));

    List<PartPlan> parts = new ArrayList<>();
    for (int i = 0; i < in.parts().size(); i++) {
      PartRequest req = in.parts().get(i);
      PartPlan pp = new PartPlan(req, i);
      for (Candidate c : pickItems(req)) {
        pp.items.add(
            new PlannedItem(
                c,
                req,
                i,
                setsPerItem,
                restSeconds(c, in.intensity()),
                suggestWeight(c, in.intensity())));
      }
      if (!pp.items.isEmpty()) parts.add(pp);
    }
    if (parts.isEmpty()) {
      return new PlanResult(Status.NO_CANDIDATES, List.of(), 0);
    }

    // 1部位の上限
    for (PartPlan pp : parts) {
      while (pp.totalSets() > AutogenRules.MAX_SETS_PER_PART) {
        if (!reduceOneSet(List.of(pp), false) && !reduceOneSet(List.of(pp), true)) {
          if (pp.items.size() <= 1) break;
          pp.items.remove(pp.items.size() - 1);
        }
      }
    }

    // 種目数の上限
    while (totalItems(parts) > AutogenRules.MAX_ITEMS) {
      if (!removeOneItemOrPart(parts)) break;
    }

    // 1セッションの上限
    while (totalSets(parts) > AutogenRules.MAX_SETS_PER_SESSION) {
      if (!reduceOneSet(parts, false)
          && !reduceOneSet(parts, true)
          && !removeOneItemOrPart(parts)) {
        break;
      }
    }

    // 使える時間に収める（削る順番: 単関節の種目 → 単関節のセット → 複合のセット → 指定の無い部位 → 最低構成判定）
    int available = in.availableMinutes() * 60;
    while (estimateSeconds(in.availableMinutes(), in.reps(), ordered(parts)) > available) {
      if (removeIsolationItem(parts)) continue;
      if (reduceOneSet(parts, false)) continue;
      if (reduceOneSet(parts, true)) continue;
      if (removeExtraItem(parts)) continue;
      if (removePart(parts, false)) continue;
      if (removePart(parts, true)) continue;
      break;
    }

    List<PlannedItem> items = ordered(parts);
    int estimated = estimateSeconds(in.availableMinutes(), in.reps(), items);
    if (estimated > available) {
      return new PlanResult(Status.TOO_SHORT, List.of(), estimated);
    }

    Set<String> planned = new HashSet<>();
    items.forEach(i -> planned.add(i.candidate.itemName()));
    for (PlannedItem item : items) {
      item.alternatives =
          item.part.candidates().stream()
              .filter(c -> !planned.contains(c.itemName()))
              .limit(AutogenRules.MAX_ALTERNATIVES)
              .map(
                  c ->
                      new Alternative(
                          c, suggestWeight(c, in.intensity()), restSeconds(c, in.intensity())))
              .toList();
    }
    return new PlanResult(Status.OK, items, estimated);
  }

  // ===================== 種目の選び方 =====================

  /**
   * 部位の重みの数だけ選ぶ。プログラムの当日の種目があれば登録順に重みの数まで最優先で使い（残りは入れ替え候補の先頭になる）、
   * 足りない枠は従来のルール（複合が無ければ複合を1つ、次は単関節、無ければ次の複合）で埋める。
   */
  static List<Candidate> pickItems(PartRequest req) {
    int count = AutogenRules.itemsForPart(req.partCode());
    List<Candidate> picked =
        new ArrayList<>(
            req.candidates()
                .subList(
                    0, Math.min(Math.min(req.programItemCount(), count), req.candidates().size())));
    if (picked.size() < count && picked.stream().noneMatch(Candidate::compound)) {
      req.candidates().stream()
          .filter(c -> c.compound() && !picked.contains(c))
          .findFirst()
          .ifPresent(picked::add);
    }
    if (picked.size() < count) {
      req.candidates().stream()
          .filter(c -> !c.compound() && !picked.contains(c))
          .findFirst()
          .ifPresent(picked::add);
    }
    for (Candidate c : req.candidates()) {
      if (picked.size() >= count) break;
      if (!picked.contains(c)) picked.add(c);
    }
    return picked;
  }

  /** 並び: 体幹は最後、複合→単関節、部位の優先度順（指定があれば指定順、無ければ大きい部位→小さい部位）。 */
  private static List<PlannedItem> ordered(List<PartPlan> parts) {
    List<PlannedItem> all = new ArrayList<>();
    for (PartPlan pp : parts) all.addAll(pp.items);
    all.sort(
        Comparator.comparingInt(
                (PlannedItem i) -> AutogenRules.CORE_PARTS.contains(i.candidate.partCode()) ? 1 : 0)
            .thenComparingInt(i -> i.candidate.compound() ? 0 : 1)
            .thenComparingInt(i -> i.partPriority));
    return all;
  }

  // ===================== 削る処理（いずれも優先度の低い部位・後ろの種目から） =====================

  /** 2種目以上ある部位の単関節種目を1つ削る（各部位1種目は残す）。 */
  private static boolean removeIsolationItem(List<PartPlan> parts) {
    for (int p = parts.size() - 1; p >= 0; p--) {
      PartPlan pp = parts.get(p);
      if (pp.items.size() <= 1) continue;
      for (int i = pp.items.size() - 1; i >= 0; i--) {
        if (!pp.items.get(i).candidate.compound()) {
          pp.items.remove(i);
          return true;
        }
      }
    }
    return false;
  }

  /** 2種目以上ある部位の後ろの種目を1つ削る（複合が2つ並んだ部位向け）。 */
  private static boolean removeExtraItem(List<PartPlan> parts) {
    for (int p = parts.size() - 1; p >= 0; p--) {
      PartPlan pp = parts.get(p);
      if (pp.items.size() > 1) {
        pp.items.remove(pp.items.size() - 1);
        return true;
      }
    }
    return false;
  }

  /** 部位を1つ削る。指定の部位は最低1部位を残す。 */
  private static boolean removePart(List<PartPlan> parts, boolean specified) {
    if (parts.size() <= 1) return false;
    for (int p = parts.size() - 1; p >= 0; p--) {
      if (parts.get(p).request.specified() == specified) {
        parts.remove(p);
        return true;
      }
    }
    return false;
  }

  /** 種目数の上限に収めるための削除: 単関節の種目 → 後ろの種目 → 指定の無い部位 → 指定の部位。 */
  private static boolean removeOneItemOrPart(List<PartPlan> parts) {
    return removeIsolationItem(parts)
        || removeExtraItem(parts)
        || removePart(parts, false)
        || removePart(parts, true);
  }

  /** 単関節（compound=false）または複合のセットを1つ減らす（下限2セット）。 */
  private static boolean reduceOneSet(List<PartPlan> parts, boolean compound) {
    for (int p = parts.size() - 1; p >= 0; p--) {
      List<PlannedItem> items = parts.get(p).items;
      for (int i = items.size() - 1; i >= 0; i--) {
        PlannedItem it = items.get(i);
        if (it.candidate.compound() == compound && it.sets > AutogenRules.MIN_SETS_PER_ITEM) {
          it.sets--;
          return true;
        }
      }
    }
    return false;
  }

  private static int totalItems(List<PartPlan> parts) {
    return parts.stream().mapToInt(pp -> pp.items.size()).sum();
  }

  private static int totalSets(List<PartPlan> parts) {
    return parts.stream().mapToInt(PartPlan::totalSets).sum();
  }

  // ===================== 計算式 =====================

  /** 所要時間(秒) = 全身ウォームアップ + Σ[準備・移動 + セット数×(回数×4秒 + 休憩) − 休憩] + 最初の複合種目のウォームアップセット枠。 */
  public static int estimateSeconds(int availableMinutes, int reps, List<PlannedItem> items) {
    int total =
        availableMinutes < AutogenRules.WARMUP_SHORT_THRESHOLD_MINUTES
            ? AutogenRules.WARMUP_SECONDS_SHORT
            : AutogenRules.WARMUP_SECONDS;
    boolean hasCompound = false;
    for (PlannedItem it : items) {
      total += itemSeconds(it.candidate.compound(), it.sets, reps, it.restSeconds);
      hasCompound |= it.candidate.compound();
    }
    if (hasCompound) total += AutogenRules.FIRST_COMPOUND_WARMUP_SET_SECONDS;
    return total;
  }

  static int itemSeconds(boolean compound, int sets, int reps, int restSeconds) {
    int prep = compound ? AutogenRules.PREP_SECONDS_COMPOUND : AutogenRules.PREP_SECONDS_ISOLATION;
    return prep + sets * (reps * AutogenRules.SECONDS_PER_REP + restSeconds) - restSeconds;
  }

  /** 休憩は休憩タイマーと同じ（ユーザー上書き優先、無ければ複合/単関節×強度の帯）。時間不足でも削らない。 */
  static int restSeconds(Candidate c, double intensity) {
    if (c.restOverrideSeconds() != null) return c.restOverrideSeconds();
    return RestIntervalCalculationService.systemRecommendedSeconds(c.compound(), intensity);
  }

  /** 重量の案。推定1RM×強度を器具の刻みに切り捨て。推定1RMが無く過去の本番セットがあれば直近の重量をそのまま。どちらも無ければ空欄。自重種目は回数のみ。 */
  public static WeightSuggestion suggestWeight(Candidate c, double intensity) {
    if (c.bodyweight()) return new WeightSuggestion(null, WeightSource.BODYWEIGHT);
    if (c.oneRm() != null && c.oneRm() > 0) {
      return new WeightSuggestion(
          floorToIncrement(c.oneRm() * intensity, c.weightIncrementKg()),
          WeightSource.ESTIMATED_1RM);
    }
    if (c.lastMainWeight() != null && c.lastMainWeight() > 0) {
      return new WeightSuggestion(c.lastMainWeight(), WeightSource.LAST_MAIN_SET);
    }
    return new WeightSuggestion(null, WeightSource.NONE);
  }

  /** 刻みに切り捨てる。刻み未満になる場合は刻み1つ分。 */
  public static double floorToIncrement(double weight, double increment) {
    double inc = increment > 0 ? increment : AutogenRules.DEFAULT_WEIGHT_INCREMENT_KG;
    // 175×0.7=122.49999…のような浮動小数点の誤差で1段下に落ちないよう、先に小数6桁へ丸める
    BigDecimal steps =
        BigDecimal.valueOf(weight)
            .setScale(6, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(inc), 0, RoundingMode.FLOOR);
    BigDecimal floored = steps.multiply(BigDecimal.valueOf(inc));
    if (floored.compareTo(BigDecimal.valueOf(inc)) < 0) floored = BigDecimal.valueOf(inc);
    return floored.setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  /** プログラムの強度%を使うときの回数: その%での最大回数から余裕分を引いた値まで（目的別の回数を超えない）。 */
  public static int programReps(int goalReps, double maxRepsAtIntensity) {
    // 75%→9.999…回のような浮動小数点の誤差を吸収してから切り捨てる
    int cap = (int) Math.floor(maxRepsAtIntensity + 1e-9) - AutogenRules.PROGRAM_REPS_MARGIN;
    return Math.max(1, Math.min(goalReps, cap));
  }
}
