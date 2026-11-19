package com.example.traning.follow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.traning.follow.MemberFollowEvaluator.Facts;
import com.example.traning.follow.MemberFollowEvaluator.Result;
import com.example.traning.follow.MemberFollowEvaluator.State;
import com.example.traning.goal.FrequencyGoalService.WeekResult;
import com.example.traning.smarttrainer.autogen.UserExcludedItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 機能見直し-1-#12: 「フォロー推奨」の印の条件と「対応済み」のあとの扱い（Q12-2）。 */
class MemberFollowEvaluatorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
  private static final LocalDate W_PREV = LocalDate.of(2026, 11, 9); // 先週の月曜
  private static final LocalDate W_PREV2 = LocalDate.of(2026, 11, 2); // 先々週の月曜

  // ===================== 条件 =====================

  @Test
  void 最後の記録から14日以上で印が付き13日では付かない() {
    Result r14 = evaluate(facts().last(TODAY.minusDays(14)));
    assertThat(r14.state()).isEqualTo(State.FLAGGED);
    assertThat(r14.reasons()).containsExactly("最後の記録から14日");
    assertThat(r14.activeTypes()).containsExactly(FollowReasonType.NO_RECENT_RECORD);

    assertThat(evaluate(facts().last(TODAY.minusDays(13))).state()).isEqualTo(State.NONE);
  }

  @Test
  void 記録が無い会員は担当になってから14日で印が付く() {
    Result r = evaluate(facts().noRecords(TODAY.minusDays(14), true));
    assertThat(r.reasons()).containsExactly("担当になってから14日、記録がありません");
    assertThat(evaluate(facts().noRecords(TODAY.minusDays(13), true)).state())
        .isEqualTo(State.NONE);
  }

  @Test
  void 担当になった日が不明または未割当の会員は登録日を起点にする() {
    Result r = evaluate(facts().noRecords(TODAY.minusDays(30), false));
    assertThat(r.reasons()).containsExactly("登録から30日、記録がありません");
    assertThat(r.activeTypes()).containsExactly(FollowReasonType.NO_RECORDS);
  }

  @Test
  void 達成が2週続けて目標の半分未満なら印が付く() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(2))
                .weeks(new WeekResult(W_PREV2, 3, 0, true), new WeekResult(W_PREV, 3, 1, true)));
    assertThat(r.reasons()).containsExactly("2週続けて目標の半分未満（0回・1回）");
  }

  @Test
  void 半分ちょうどや1週だけの未達では印が付かない() {
    // 週4回の2回は半分ちょうど（未満ではない）
    assertThat(
            evaluate(
                    facts()
                        .last(TODAY.minusDays(2))
                        .weeks(
                            new WeekResult(W_PREV2, 4, 2, true),
                            new WeekResult(W_PREV, 4, 0, true)))
                .state())
        .isEqualTo(State.NONE);
    assertThat(
            evaluate(facts().last(TODAY.minusDays(2)).weeks(new WeekResult(W_PREV, 3, 0, true)))
                .state())
        .isEqualTo(State.NONE);
  }

  @Test
  void 週の目標が無い会員や目標なしの週や設定した週を含む場合は達成率の条件を判定しない() {
    assertThat(evaluate(facts().last(TODAY.minusDays(2))).state()).isEqualTo(State.NONE);
    assertThat(
            evaluate(
                    facts()
                        .last(TODAY.minusDays(2))
                        .weeks(
                            new WeekResult(W_PREV2, 0, 0, true),
                            new WeekResult(W_PREV, 3, 0, true)))
                .state())
        .isEqualTo(State.NONE);
    assertThat(
            evaluate(
                    facts()
                        .last(TODAY.minusDays(2))
                        .weeks(
                            new WeekResult(W_PREV2, 3, 0, false),
                            new WeekResult(W_PREV, 3, 0, true)))
                .state())
        .isEqualTo(State.NONE);
  }

  @Test
  void 目標なしの会員でも14日と痛みの条件は判定する() {
    Result r = evaluate(facts().last(TODAY.minusDays(20)).pain("ショルダープレス", "右肩に違和感", 1, true));
    assertThat(r.reasons()).containsExactly("最後の記録から20日", "痛みの報告（ショルダープレスを除外、理由「右肩に違和感」）");
  }

  @Test
  void 痛みの理由を見られない場合は除外を追加とだけ出す() {
    Result r = evaluate(facts().last(TODAY.minusDays(1)).pain("ショルダープレス", "右肩に違和感", 1, false));
    assertThat(r.reasons()).containsExactly("痛みの報告（除外を追加）");
    assertThat(String.join("", r.reasons())).doesNotContain("右肩").doesNotContain("ショルダープレス");
  }

  @Test
  void デロード週は条件にあてはまっても判定しない() {
    Result r = evaluate(facts().last(TODAY.minusDays(30)).pain("スクワット", "膝", 1, true).deload());
    assertThat(r.state()).isEqualTo(State.NOT_EVALUATED_DELOAD);
    assertThat(r.reasons()).isEmpty();
    assertThat(r.activeTypes()).isEmpty();
  }

  // ===================== 対応済み（Q12-2 A） =====================

  @Test
  void 対応済みから14日未満は同じ状態が続いても印を出さず対応済みと出す() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(18))
                .ack("NO_RECENT_RECORD", TODAY.minusDays(4).atTime(10, 0), "山本 健"));
    assertThat(r.state()).isEqualTo(State.ACKNOWLEDGED);
    assertThat(r.acknowledgementLabel()).isEqualTo("対応済み（11/15 山本 健）");
    assertThat(r.flagged()).isFalse();
  }

  @Test
  void 対応済みから14日たっても同じ状態ならまた印を付け前回の対応を出す() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(28))
                .ack("NO_RECENT_RECORD", TODAY.minusDays(14).atTime(10, 0), "山本 健"));
    assertThat(r.state()).isEqualTo(State.FLAGGED);
    assertThat(r.reasons()).containsExactly("最後の記録から28日（前回の対応から14日）");
    assertThat(r.acknowledgementLabel()).isEqualTo("前回: 対応済み 11/5 山本 健");
  }

  @Test
  void 対応済みのあとに別の理由が新しく出たらすぐ印を付ける() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(18))
                .ack("NO_RECENT_RECORD", TODAY.minusDays(4).atTime(10, 0), "山本 健")
                .pain("ベンチプレス", "肘が痛い", 1, true));
    assertThat(r.state()).isEqualTo(State.FLAGGED);
    // 対応済みの理由（14日未満）は出さず、新しい理由だけ出す
    assertThat(r.reasons()).containsExactly("痛みの報告（ベンチプレスを除外、理由「肘が痛い」）");
    assertThat(r.activeTypes())
        .containsExactlyInAnyOrder(FollowReasonType.NO_RECENT_RECORD, FollowReasonType.PAIN_REPORT);
  }

  @Test
  void 対応済みのあとに新しい除外が追加されたら同じ種類でも新しい報告として印を付ける() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(1))
                .ack("PAIN_REPORT", TODAY.minusDays(3).atTime(10, 0), "山本 健")
                .pain("ベンチプレス", "肘が痛い", 5, true) // 対応前の報告
                .pain("スクワット", "膝が痛い", 1, true)); // 対応後の報告
    assertThat(r.state()).isEqualTo(State.FLAGGED);
    assertThat(r.reasons()).hasSize(2);
  }

  @Test
  void 対応済みのあとに記録があってまた14日空いたら新しい状態として印を付ける() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(14))
                .ack("NO_RECENT_RECORD", TODAY.minusDays(20).atTime(10, 0), "山本 健"));
    assertThat(r.reasons()).containsExactly("最後の記録から14日");
  }

  @Test
  void 対応済みから時間がたち条件も無ければ何も出さない() {
    Result r =
        evaluate(
            facts()
                .last(TODAY.minusDays(1))
                .ack("NO_RECENT_RECORD", TODAY.minusDays(30).atTime(10, 0), "山本 健"));
    assertThat(r.state()).isEqualTo(State.NONE);
    assertThat(r.acknowledgementLabel()).isNull();
  }

  // ===================== helpers =====================

  private static Result evaluate(Builder b) {
    return MemberFollowEvaluator.evaluate(b.build(), TODAY);
  }

  private static Builder facts() {
    return new Builder();
  }

  private static final class Builder {
    private LocalDate last;
    private LocalDate base = TODAY.minusDays(100);
    private boolean baseIsAssignment;
    private List<WeekResult> weeks = List.of();
    private boolean deload;
    private final java.util.ArrayList<UserExcludedItem> pain = new java.util.ArrayList<>();
    private boolean painVisible;
    private MemberFollowAcknowledgement ack;

    Builder last(LocalDate d) {
      last = d;
      return this;
    }

    Builder noRecords(LocalDate b, boolean isAssignment) {
      last = null;
      base = b;
      baseIsAssignment = isAssignment;
      return this;
    }

    Builder weeks(WeekResult... w) {
      weeks = List.of(w);
      return this;
    }

    Builder deload() {
      deload = true;
      return this;
    }

    Builder pain(String item, String reason, int daysAgo, boolean visible) {
      UserExcludedItem e = new UserExcludedItem();
      e.setItemName(item);
      e.setReason(reason);
      e.setCreatedAt(TODAY.minusDays(daysAgo).atTime(9, 0));
      pain.add(e);
      painVisible = visible;
      return this;
    }

    Builder ack(String types, LocalDateTime at, String name) {
      ack = new MemberFollowAcknowledgement();
      ack.setReasonTypes(types);
      ack.setActedAt(at);
      ack.setActedByName(name);
      return this;
    }

    Facts build() {
      return new Facts(last, base, baseIsAssignment, weeks, deload, pain, painVisible, ack);
    }
  }
}
