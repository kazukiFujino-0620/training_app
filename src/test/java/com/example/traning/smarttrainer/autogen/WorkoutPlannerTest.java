package com.example.traning.smarttrainer.autogen;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.smarttrainer.autogen.WorkoutPlanner.Candidate;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PartRequest;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlanInput;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlanResult;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.PlannedItem;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.Status;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.WeightSource;
import com.example.traning.smarttrainer.autogen.WorkoutPlanner.WeightSuggestion;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 機能見直し-1-#4: メニュー自動作成の組み立て（詳細設計書5-2節の数値）。 */
class WorkoutPlannerTest {

  /** 増量目的: 強度70%（休憩は複合120秒・単関節90秒）、10回、4セット。 */
  private static final double INTENSITY = 0.70;

  private static final int REPS = 10;
  private static final int SETS = 4;

  private static Candidate comp(String name, String part) {
    return new Candidate(name, part, true, false, 2.5, null, null, null);
  }

  private static Candidate iso(String name, String part) {
    return new Candidate(name, part, false, false, 2.5, null, null, null);
  }

  /** 複合2つ・単関節2つの候補を持つ部位。 */
  private static PartRequest part(String code, boolean specified) {
    return new PartRequest(
        code,
        specified,
        false,
        List.of(
            comp(code + "_C1", code),
            comp(code + "_C2", code),
            iso(code + "_I1", code),
            iso(code + "_I2", code)));
  }

  private static PlanResult plan(int minutes, int baseSets, PartRequest... parts) {
    return WorkoutPlanner.plan(
        new PlanInput(minutes, List.of(parts), INTENSITY, REPS, baseSets, 0));
  }

  private static List<String> names(PlanResult r) {
    return r.items().stream().map(i -> i.candidate().itemName()).toList();
  }

  private static List<Integer> sets(PlanResult r) {
    return r.items().stream().map(PlannedItem::sets).toList();
  }

  private static int totalSets(PlanResult r) {
    return r.items().stream().mapToInt(PlannedItem::sets).sum();
  }

  @Nested
  class 所要時間の見積もり {

    @Test
    void 複合1種目_ウォームアップ5分と最初の複合種目のウォームアップセット枠3分を足す() {
      // 300 + [120 + 4×(10×4 + 120) − 120] + 180 = 300 + 640 + 180
      PlanResult r =
          plan(60, SETS, new PartRequest("SHOULDER", true, false, List.of(comp("A", "SHOULDER"))));
      assertThat(r.estimatedSeconds()).isEqualTo(1120);
    }

    @Test
    void 使える時間が20分未満なら全身ウォームアップは3分() {
      PartRequest p = new PartRequest("SHOULDER", true, false, List.of(comp("A", "SHOULDER")));
      // 複合2セット: 120 + 2×160 − 120 = 320
      assertThat(plan(19, 2, p).estimatedSeconds()).isEqualTo(180 + 320 + 180);
      assertThat(plan(20, 2, p).estimatedSeconds()).isEqualTo(300 + 320 + 180);
    }

    @Test
    void 単関節は準備1分_休憩90秒_単関節だけならウォームアップセット枠は足さない() {
      PartRequest p = new PartRequest("ARM", true, false, List.of(iso("A", "ARM")));
      // 300 + [60 + 3×(40 + 90) − 90] = 300 + 360
      assertThat(plan(60, 3, p).estimatedSeconds()).isEqualTo(660);
    }

    @Test
    void ユーザーの休憩の上書きを使う() {
      Candidate c = new Candidate("A", "SHOULDER", true, false, 2.5, null, null, 60);
      PlanResult r = plan(60, 2, new PartRequest("SHOULDER", true, false, List.of(c)));
      assertThat(r.items().get(0).restSeconds()).isEqualTo(60);
      // 300 + [120 + 2×(40 + 60) − 60] + 180
      assertThat(r.estimatedSeconds()).isEqualTo(300 + 260 + 180);
    }

    @Test
    void 減量目的の強度60パーセントでは休憩タイマーと同じく複合90秒_単関節60秒() {
      PlanResult r =
          WorkoutPlanner.plan(new PlanInput(60, List.of(part("CHEST", true)), 0.60, 12, 3, 0));
      Map<String, Integer> rest =
          r.items().stream()
              .collect(Collectors.toMap(i -> i.candidate().itemName(), PlannedItem::restSeconds));
      assertThat(rest).containsEntry("CHEST_C1", 90).containsEntry("CHEST_I1", 60);
    }
  }

  @Nested
  class 部位配分と上限 {

    @Test
    void 大きい部位は2種目_小さい部位は1種目で_各部位最低1種目() {
      PlanResult r = plan(90, SETS, part("CHEST", true), part("ARM", true));
      assertThat(names(r)).containsExactly("CHEST_C1", "ARM_C1", "CHEST_I1");
    }

    @Test
    void 大きい部位の2種目目は単関節_単関節が無ければ次の複合() {
      PartRequest onlyCompounds =
          new PartRequest("BACK", true, false, List.of(comp("B1", "BACK"), comp("B2", "BACK")));
      assertThat(names(plan(90, SETS, onlyCompounds))).containsExactly("B1", "B2");
    }

    @Test
    void プログラムの当日の種目が複合2つなら_単関節より優先して両方使う() {
      PartRequest leg =
          new PartRequest(
              "LEG",
              true,
              false,
              List.of(
                  comp("RDL", "LEG"),
                  comp("LEGPRESS", "LEG"),
                  comp("SQUAT", "LEG"),
                  iso("LEGEXT", "LEG")),
              2);
      PlanResult r = plan(90, SETS, leg);
      assertThat(names(r)).containsExactly("RDL", "LEGPRESS");
      assertThat(r.items().get(0).alternatives())
          .extracting(a -> a.candidate().itemName())
          .startsWith("SQUAT");
    }

    @Test
    void プログラムの当日の種目が重みより多ければ登録順で重みの数まで_残りは入れ替え候補の先頭() {
      PartRequest leg =
          new PartRequest(
              "LEG",
              true,
              false,
              List.of(
                  comp("RDL", "LEG"),
                  comp("LEGPRESS", "LEG"),
                  iso("LEGCURL", "LEG"),
                  iso("LEGEXT", "LEG")),
              3);
      PlanResult r = plan(90, SETS, leg);
      assertThat(names(r)).containsExactly("RDL", "LEGPRESS");
      assertThat(r.items())
          .allSatisfy(
              i ->
                  assertThat(i.alternatives())
                      .extracting(a -> a.candidate().itemName())
                      .first()
                      .isEqualTo("LEGCURL"));
    }

    @Test
    void プログラムの当日の種目が1つなら2つ目は従来のルール() {
      PartRequest leg =
          new PartRequest(
              "LEG",
              true,
              false,
              List.of(comp("RDL", "LEG"), comp("LEGPRESS", "LEG"), iso("LEGEXT", "LEG")),
              1);
      assertThat(names(plan(90, SETS, leg))).containsExactly("RDL", "LEGEXT");

      PartRequest isoFirst =
          new PartRequest(
              "LEG",
              true,
              false,
              List.of(iso("LEGCURL", "LEG"), comp("RDL", "LEG"), iso("LEGEXT", "LEG")),
              1);
      // プログラム種目が単関節1つなら、残りの枠は複合を補う（並びは複合→単関節）
      assertThat(names(plan(90, SETS, isoFirst))).containsExactly("RDL", "LEGCURL");
    }

    @Test
    void プログラム種目でも時間が足りなければ従来の順番で削る() {
      PartRequest leg =
          new PartRequest(
              "LEG",
              true,
              false,
              List.of(comp("RDL", "LEG"), comp("LEGPRESS", "LEG"), iso("LEGEXT", "LEG")),
              2);
      // 15分: 単関節が無いので複合のセットを減らし、最後に部位内の2種目目を削る
      PlanResult r = plan(15, SETS, leg);
      assertThat(names(r)).containsExactly("RDL");
    }

    @Test
    void 複合が無い部位は単関節から選ぶ() {
      PartRequest onlyIso =
          new PartRequest("ARM", true, false, List.of(iso("A1", "ARM"), iso("A2", "ARM")));
      assertThat(names(plan(90, SETS, onlyIso))).containsExactly("A1");
    }

    @Test
    void 種目は6種目まで_単関節の種目から削る() {
      // 脚2+背中2+胸2+肩1+腕1=8種目 → 優先度の低い胸・背中の単関節を削って6種目
      PlanResult r =
          plan(
              90,
              SETS,
              part("LEG", false),
              part("BACK", false),
              part("CHEST", false),
              part("SHOULDER", false),
              part("ARM", false));
      assertThat(r.status()).isEqualTo(Status.OK);
      assertThat(r.items()).hasSize(6);
      assertThat(names(r))
          .containsExactlyInAnyOrder(
              "LEG_C1", "LEG_I1", "BACK_C1", "CHEST_C1", "SHOULDER_C1", "ARM_C1");
      assertThat(totalSets(r)).isEqualTo(24);
    }

    @Test
    void セッション24セット_部位10セットを超えない() {
      PlanResult r =
          plan(
              240,
              6,
              part("LEG", false),
              part("BACK", false),
              part("CHEST", false),
              part("SHOULDER", false),
              part("ARM", false));
      assertThat(totalSets(r)).isLessThanOrEqualTo(AutogenRules.MAX_SETS_PER_SESSION);
      Map<String, Integer> byPart =
          r.items().stream()
              .collect(
                  Collectors.groupingBy(
                      i -> i.candidate().partCode(), Collectors.summingInt(PlannedItem::sets)));
      assertThat(byPart.values()).allMatch(v -> v <= AutogenRules.MAX_SETS_PER_PART);
    }

    @Test
    void 時間が余っても種目やセットを埋めない() {
      PlanResult r = plan(240, SETS, part("ARM", true));
      assertThat(names(r)).containsExactly("ARM_C1");
      assertThat(sets(r)).containsExactly(SETS);
      assertThat(r.estimatedSeconds()).isLessThan(240 * 60);
    }

    @Test
    void 候補が1つも無ければ組めない() {
      PlanResult r = plan(60, SETS, new PartRequest("CHEST", true, false, List.of()));
      assertThat(r.status()).isEqualTo(Status.NO_CANDIDATES);
      assertThat(r.items()).isEmpty();
    }

    @Test
    void 候補の無い部位は飛ばして他の部位で組む() {
      PlanResult r =
          plan(60, SETS, new PartRequest("CHEST", true, false, List.of()), part("ARM", true));
      assertThat(names(r)).containsExactly("ARM_C1");
    }
  }

  @Nested
  class 削る順番 {

    private final PartRequest chest = part("CHEST", true);
    private final PartRequest arm =
        new PartRequest("ARM", true, false, List.of(iso("ARM_I1", "ARM"), iso("ARM_I2", "ARM")));

    @Test
    void 時間内なら削らない() {
      // 300 + 胸複合640 + 胸単関節490 + 腕単関節490 + 180 = 2100
      PlanResult r = plan(35, SETS, chest, arm);
      assertThat(names(r)).containsExactly("CHEST_C1", "CHEST_I1", "ARM_I1");
      assertThat(r.estimatedSeconds()).isEqualTo(2100);
    }

    @Test
    void まず単関節の種目を削る_各部位1種目は残す() {
      // 30分=1800秒: 胸の単関節を削ると 300+640+490+180=1610
      PlanResult r = plan(30, SETS, chest, arm);
      assertThat(names(r)).containsExactly("CHEST_C1", "ARM_I1");
      assertThat(sets(r)).containsExactly(4, 4);
    }

    @Test
    void 次に単関節のセットを減らす() {
      // 25分=1500秒: 腕4→3セット(360) で 1480
      PlanResult r = plan(25, SETS, chest, arm);
      assertThat(names(r)).containsExactly("CHEST_C1", "ARM_I1");
      assertThat(sets(r)).containsExactly(4, 3);
    }

    @Test
    void 単関節が2セットになってから複合のセットを減らす() {
      // 20分=1200秒: 腕→2セット(230)=1350、胸→3セット(480)=1190
      PlanResult r = plan(20, SETS, chest, arm);
      assertThat(sets(r)).containsExactly(3, 2);
    }

    @Test
    void 指定が複数なら指定順の後ろの部位から削り_最低構成で判定する() {
      // 15分=900秒: 全種目2セットでも 180+320+230+180=910 → 後ろに指定した腕を削る
      PlanResult r = plan(15, SETS, chest, arm);
      assertThat(r.status()).isEqualTo(Status.OK);
      assertThat(names(r)).containsExactly("CHEST_C1");
      assertThat(sets(r)).containsExactly(2);
      assertThat(r.estimatedSeconds()).isEqualTo(680);
    }

    @Test
    void 指定の無い部位は指定した部位より先に削る() {
      // おまかせで追加された腕（指定なし）を、指定した胸より先に削る
      PartRequest unspecifiedArm =
          new PartRequest("ARM", false, false, List.of(comp("ARM_C1", "ARM")));
      PartRequest specifiedChest =
          new PartRequest("CHEST", true, false, List.of(comp("CHEST_C1", "CHEST")));
      PlanResult r = plan(15, SETS, unspecifiedArm, specifiedChest);
      assertThat(names(r)).containsExactly("CHEST_C1");
    }

    @Test
    void おまかせで短い時間なら優先度の高い部位だけ残る() {
      PlanResult r =
          plan(
              15,
              SETS,
              part("LEG", false),
              part("BACK", false),
              part("CHEST", false),
              part("SHOULDER", false),
              part("ARM", false));
      assertThat(names(r)).containsExactly("LEG_C1");
      assertThat(sets(r)).containsExactly(2);
    }

    @Test
    void 指定1部位で15分なら複合1種目に減らして組める() {
      // 単関節を削り、複合4→3セット: 180+480+180=840
      PlanResult r = plan(15, SETS, chest);
      assertThat(names(r)).containsExactly("CHEST_C1");
      assertThat(sets(r)).containsExactly(3);
    }

    @Test
    void 最低構成が入らなければ時間が短いため生成できない() {
      Candidate longRest = new Candidate("X", "CHEST", true, false, 2.5, null, null, 600);
      PlanResult r = plan(15, SETS, new PartRequest("CHEST", true, false, List.of(longRest)));
      assertThat(r.status()).isEqualTo(Status.TOO_SHORT);
      assertThat(r.items()).isEmpty();
    }
  }

  @Nested
  class 並び順 {

    @Test
    void 複合から単関節_指定順を部位の優先度にする() {
      PlanResult r = plan(90, SETS, part("ARM", true), part("CHEST", true));
      assertThat(names(r)).containsExactly("ARM_C1", "CHEST_C1", "CHEST_I1");
    }

    @Test
    void 大きい部位から小さい部位の順に渡せばその順に並ぶ() {
      PlanResult r = plan(90, SETS, part("LEG", false), part("SHOULDER", false));
      assertThat(names(r)).containsExactly("LEG_C1", "SHOULDER_C1", "LEG_I1");
    }

    @Test
    void 体幹は複合でも最後() {
      PartRequest abs = new PartRequest("ABS", true, false, List.of(comp("ABS_C1", "ABS")));
      PlanResult r = plan(90, SETS, abs, part("CHEST", true));
      assertThat(names(r)).containsExactly("CHEST_C1", "CHEST_I1", "ABS_C1");
    }
  }

  @Nested
  class 疲労 {

    @Test
    void 全部位が高疲労のときは各種目のセットを1つ減らす() {
      PlanResult r =
          WorkoutPlanner.plan(
              new PlanInput(90, List.of(part("CHEST", false)), INTENSITY, REPS, SETS, 1));
      assertThat(sets(r)).containsOnly(3);
    }

    @Test
    void 減らしても2セットは下回らない() {
      PlanResult r =
          WorkoutPlanner.plan(
              new PlanInput(90, List.of(part("CHEST", false)), INTENSITY, REPS, 2, 1));
      assertThat(sets(r)).containsOnly(2);
    }
  }

  @Nested
  class 重量 {

    @Test
    void 推定1RMに強度を掛けてバーベルの刻み2_5kgに切り捨てる() {
      Candidate c = new Candidate("A", "LEG", true, false, 2.5, 120.0, null, null);
      // 120 × 0.7 = 84 → 82.5
      assertThat(WorkoutPlanner.suggestWeight(c, 0.70))
          .isEqualTo(new WeightSuggestion(82.5, WeightSource.ESTIMATED_1RM));
    }

    @Test
    void 刻みはダンベル2kg_マシン5kgでも切り捨て() {
      assertThat(WorkoutPlanner.floorToIncrement(24.5, 2.0)).isEqualTo(24.0);
      assertThat(WorkoutPlanner.floorToIncrement(65.1, 5.0)).isEqualTo(65.0);
      assertThat(WorkoutPlanner.floorToIncrement(7.0, 1.0)).isEqualTo(7.0);
    }

    @Test
    void ちょうど刻みの値はそのまま_刻み未満は刻み1つ分() {
      assertThat(WorkoutPlanner.floorToIncrement(82.5, 2.5)).isEqualTo(82.5);
      // 175×0.7 は double で 122.49999… になるが 122.5 として扱う
      assertThat(WorkoutPlanner.floorToIncrement(175 * 0.7, 2.5)).isEqualTo(122.5);
      assertThat(WorkoutPlanner.floorToIncrement(1.0, 2.5)).isEqualTo(2.5);
    }

    @Test
    void 刻みが不明なら2_5kg() {
      assertThat(WorkoutPlanner.floorToIncrement(84.0, 0)).isEqualTo(82.5);
    }

    @Test
    void 推定1RMが無く過去の本番セットがあれば直近の重量をそのまま() {
      Candidate c = new Candidate("A", "LEG", true, false, 2.5, null, 41.0, null);
      assertThat(WorkoutPlanner.suggestWeight(c, 0.70))
          .isEqualTo(new WeightSuggestion(41.0, WeightSource.LAST_MAIN_SET));
    }

    @Test
    void 記録の無い種目は空欄() {
      Candidate c = new Candidate("A", "LEG", true, false, 2.5, null, null, null);
      assertThat(WorkoutPlanner.suggestWeight(c, 0.70))
          .isEqualTo(new WeightSuggestion(null, WeightSource.NONE));
    }

    @Test
    void 自重種目は記録があっても回数のみ() {
      Candidate c = new Candidate("A", "CHEST", true, true, 2.5, 80.0, 0.0, null);
      assertThat(WorkoutPlanner.suggestWeight(c, 0.70))
          .isEqualTo(new WeightSuggestion(null, WeightSource.BODYWEIGHT));
    }
  }

  @Nested
  class 休止補正と上限キャップ_Q4_12 {

    private Candidate withHistory(
        Double oneRm, Double last, Integer days, Double lastMax, Integer lastReps) {
      return new Candidate(
          "A", "LEG", true, false, 2.5, oneRm, last, null, days, lastMax, lastReps);
    }

    @Test
    void 休止の境界_27日は補正なし_28日と56日は0_9倍_57日は0_8倍() {
      assertThat(WorkoutPlanner.breakFactor(27)).isEqualTo(1.0);
      assertThat(WorkoutPlanner.breakFactor(28)).isEqualTo(0.9);
      assertThat(WorkoutPlanner.breakFactor(56)).isEqualTo(0.9);
      assertThat(WorkoutPlanner.breakFactor(57)).isEqualTo(0.8);
      assertThat(WorkoutPlanner.breakFactor(null)).isEqualTo(1.0);
    }

    @Test
    void 休止補正は推定1RMの重量に掛けて刻みに切り捨て_軽めにした印を付ける() {
      // 100 × 0.7 × 0.9 = 63 → 62.5
      WeightSuggestion w =
          WorkoutPlanner.suggestWeight(withHistory(100.0, 100.0, 28, null, null), 0.70, 10);
      assertThat(w).isEqualTo(new WeightSuggestion(62.5, WeightSource.ESTIMATED_1RM, true));
      // 100 × 0.7 × 0.8 = 56 → 55
      assertThat(
              WorkoutPlanner.suggestWeight(withHistory(100.0, 100.0, 57, null, null), 0.70, 10)
                  .weightKg())
          .isEqualTo(55.0);
      WeightSuggestion fresh =
          WorkoutPlanner.suggestWeight(withHistory(100.0, 100.0, 27, null, null), 0.70, 10);
      assertThat(fresh).isEqualTo(new WeightSuggestion(70.0, WeightSource.ESTIMATED_1RM, false));
    }

    @Test
    void 推定1RMが無く直近の重量を使う場合も休止補正をかける() {
      WeightSuggestion w =
          WorkoutPlanner.suggestWeight(withHistory(null, 41.0, 60, null, null), 0.70, 10);
      // 41 × 0.8 = 32.8 → 32.5
      assertThat(w).isEqualTo(new WeightSuggestion(32.5, WeightSource.LAST_MAIN_SET, true));
      assertThat(
              WorkoutPlanner.suggestWeight(withHistory(null, 41.0, 3, null, null), 0.70, 10)
                  .weightKg())
          .isEqualTo(41.0);
    }

    @Test
    void 提案は直近セッションの最大作業重量に1刻み足した重さまで() {
      // 推定1RM 140 × 70% = 98 → 97.5 だが、直近 80kg×10回 → 上限 82.5
      WeightSuggestion w =
          WorkoutPlanner.suggestWeight(withHistory(140.0, 80.0, 3, 80.0, 10), 0.70, 10);
      assertThat(w.weightKg()).isEqualTo(82.5);
      // 刻みはダンベル2kg
      Candidate db = new Candidate("D", "CHEST", true, false, 2.0, 50.0, 30.0, null, 3, 30.0, 10);
      assertThat(WorkoutPlanner.suggestWeight(db, 0.70, 10).weightKg()).isEqualTo(32.0);
    }

    @Test
    void 目標回数が直近セッションの回数より少ない場合はキャップを適用しない() {
      // プログラムの高強度週: 目標5回、直近は10回 → キャップなしで 140 × 0.8 = 112 → 110
      WeightSuggestion w =
          WorkoutPlanner.suggestWeight(withHistory(140.0, 80.0, 3, 80.0, 10), 0.80, 5);
      assertThat(w.weightKg()).isEqualTo(110.0);
      // 目標回数が同じならキャップ適用
      assertThat(
              WorkoutPlanner.suggestWeight(withHistory(140.0, 80.0, 3, 80.0, 10), 0.80, 10)
                  .weightKg())
          .isEqualTo(82.5);
    }

    @Test
    void キャップより軽い提案はそのまま() {
      assertThat(
              WorkoutPlanner.suggestWeight(withHistory(100.0, 80.0, 3, 80.0, 10), 0.70, 10)
                  .weightKg())
          .isEqualTo(70.0);
    }
  }

  @Nested
  class おまかせの優先度と表示順_Q4_13 {

    @Test
    void 指定の無い部位は優先度の順で削り_表示は大きい部位から() {
      // 優先度: 腕 > 胸 > 脚（脚が最近やった部位）。15分では脚・胸を外して腕だけ残る
      PlanResult r = plan(15, SETS, part("ARM", false), part("CHEST", false), part("LEG", false));
      assertThat(names(r)).containsExactly("ARM_C1");

      // 時間が足りれば表示は 脚 → 胸 → 腕（複合）→ 単関節
      PlanResult wide =
          plan(90, SETS, part("ARM", false), part("CHEST", false), part("LEG", false));
      assertThat(names(wide)).containsExactly("LEG_C1", "CHEST_C1", "ARM_C1", "LEG_I1", "CHEST_I1");
    }
  }

  @Nested
  class プログラムの強度での回数 {

    @Test
    void その強度での最大回数から2回引いた値までに抑える() {
      // Epley逆算: 70%→12.86回、80%→7.5回、60%→20回
      assertThat(WorkoutPlanner.programReps(10, 12.857)).isEqualTo(10);
      assertThat(WorkoutPlanner.programReps(10, 7.5)).isEqualTo(5);
      assertThat(WorkoutPlanner.programReps(12, 20.0)).isEqualTo(12);
    }

    @Test
    void 割り切れる強度で誤差により1回少なくならない() {
      // 75%: 30×(1/0.75−1) は double で 9.999… になるが10回として扱う
      double max =
          new com.example.traning.smarttrainer.prediction.OneRmPredictionService()
              .maxRepsAtIntensity(0.75);
      assertThat(WorkoutPlanner.programReps(10, max)).isEqualTo(8);
    }

    @Test
    void 高強度でも1回は残す() {
      assertThat(WorkoutPlanner.programReps(10, 1.58)).isEqualTo(1);
    }
  }

  @Nested
  class 入れ替え候補 {

    @Test
    void 同じ部位でメニューに入っていない候補を返す() {
      PlanResult r = plan(90, SETS, part("CHEST", true));
      PlannedItem first = r.items().get(0);
      assertThat(first.alternatives())
          .extracting(a -> a.candidate().itemName())
          .containsExactly("CHEST_C2", "CHEST_I2");
    }

    @Test
    void 入れ替え候補にも重量と休憩を付ける() {
      Candidate planned = new Candidate("P", "ARM", true, false, 2.5, null, null, null);
      Candidate alt = new Candidate("Q", "ARM", false, false, 2.0, 50.0, null, null);
      PlanResult r = plan(90, SETS, new PartRequest("ARM", true, false, List.of(planned, alt)));
      var a = r.items().get(0).alternatives().get(0);
      assertThat(a.weight().weightKg()).isEqualTo(34.0);
      assertThat(a.restSeconds()).isEqualTo(90);
    }
  }
}
