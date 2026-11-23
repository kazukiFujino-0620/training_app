package com.example.traning.follow;

import com.example.traning.goal.FrequencyGoalService.WeekResult;
import com.example.traning.smarttrainer.autogen.UserExcludedItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 「フォロー推奨」の印の判定（機能見直し-1-#12 要件定義2章・4章、Q12-2）。DBを使わない純粋な計算で、材料は {@link MemberFollowService}がまとめて集める。
 *
 * <p>条件の数値はすべて暫定値（会議の決定: 誤報の多さを見て見直す）。
 */
public final class MemberFollowEvaluator {

  /** 最後の記録からこの日数以上で印（暫定値）。 */
  public static final int NO_RECENT_RECORD_DAYS = 14;

  /** 記録が1件も無い会員は、担当になってから（不明・未割当は登録から）この日数以上で印（暫定値）。 */
  public static final int NO_RECORDS_DAYS = 14;

  /** 週の目標の達成がこの週数続けて半分未満で印（暫定値）。直近の終わった週から数える。 */
  public static final int LOW_ACHIEVEMENT_WEEKS = 2;

  /** 理由を書いた除外を、追加からこの日数のあいだ痛みの報告として扱う（暫定値）。 */
  public static final int PAIN_REPORT_DAYS = 14;

  /** 「対応済み」のあと同じ状態がこの日数続くと、また印を付ける（Q12-2 A）。 */
  public static final int REFLAG_AFTER_DAYS = 14;

  private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d");

  private MemberFollowEvaluator() {}

  /** 一覧の「フォロー推奨」欄の状態。 */
  public enum State {
    /** 印あり。 */
    FLAGGED,
    /** 対応済み（印を消している）。 */
    ACKNOWLEDGED,
    /** デロード週のため判定しない。 */
    NOT_EVALUATED_DELOAD,
    /** 印なし。 */
    NONE
  }

  /**
   * 判定の材料。
   *
   * @param lastTrainingDate 今日以前で最後のトレーニング日。記録が無ければnull
   * @param noRecordsBaseDate 記録が無い会員の起点（担当になった日、不明・未割当は登録日）。不明ならnull
   * @param baseIsAssignment 起点が担当になった日か（理由の文言を「担当になってから」にする）
   * @param lastCompletedWeeks 直近の終わった週の結果（古い週から順）
   * @param deloadWeek 今日が期分けのデロード週か
   * @param painReports 痛みの報告として扱う除外（追加から{@link #PAIN_REPORT_DAYS}日以内で理由あり）
   * @param painDetailVisible 痛みの報告の理由の文言を見せてよいか（{@code HealthInfoAccessPolicy}の判定）
   * @param latestAcknowledgement 最新の「対応済み」。無ければnull
   */
  public record Facts(
      LocalDate lastTrainingDate,
      LocalDate noRecordsBaseDate,
      boolean baseIsAssignment,
      List<WeekResult> lastCompletedWeeks,
      boolean deloadWeek,
      List<UserExcludedItem> painReports,
      boolean painDetailVisible,
      MemberFollowAcknowledgement latestAcknowledgement) {}

  /**
   * 判定結果。
   *
   * @param reasons 印の理由（FLAGGEDのときのみ）
   * @param activeTypes 今あてはまる条件の種類（「対応済みにする」で記録する。印を消している条件も含む）
   * @param acknowledgementLabel 「対応済み（11/15 山本）」または「前回: 対応済み 11/15 山本」。無ければnull
   */
  public record Result(
      State state,
      List<String> reasons,
      Set<FollowReasonType> activeTypes,
      String acknowledgementLabel) {

    public boolean flagged() {
      return state == State.FLAGGED;
    }
  }

  private record Condition(FollowReasonType type, String text, boolean continuing) {}

  public static Result evaluate(Facts facts, LocalDate today) {
    if (facts.deloadWeek()) {
      return new Result(State.NOT_EVALUATED_DELOAD, List.of(), Set.of(), null);
    }
    MemberFollowAcknowledgement ack = facts.latestAcknowledgement();
    Set<FollowReasonType> ackTypes = ack == null ? Set.of() : parseTypes(ack.getReasonTypes());
    LocalDateTime ackAt = ack == null ? null : ack.getActedAt();

    List<Condition> conditions = new ArrayList<>();
    if (facts.lastTrainingDate() != null) {
      long days = ChronoUnit.DAYS.between(facts.lastTrainingDate(), today);
      if (days >= NO_RECENT_RECORD_DAYS) {
        // 対応のあとに記録があれば、また休み始めた新しい状態として扱う
        boolean continuing =
            ackTypes.contains(FollowReasonType.NO_RECENT_RECORD)
                && !facts.lastTrainingDate().isAfter(ackAt.toLocalDate());
        conditions.add(
            new Condition(FollowReasonType.NO_RECENT_RECORD, "最後の記録から" + days + "日", continuing));
      }
    } else if (facts.noRecordsBaseDate() != null) {
      long days = ChronoUnit.DAYS.between(facts.noRecordsBaseDate(), today);
      if (days >= NO_RECORDS_DAYS) {
        String text = (facts.baseIsAssignment() ? "担当になってから" : "登録から") + days + "日、記録がありません";
        conditions.add(
            new Condition(
                FollowReasonType.NO_RECORDS, text, ackTypes.contains(FollowReasonType.NO_RECORDS)));
      }
    }

    lowAchievementText(facts.lastCompletedWeeks())
        .ifPresent(
            text ->
                conditions.add(
                    new Condition(
                        FollowReasonType.LOW_ACHIEVEMENT,
                        text,
                        ackTypes.contains(FollowReasonType.LOW_ACHIEVEMENT))));

    if (!facts.painReports().isEmpty()) {
      // 対応のあとに追加された除外があれば新しい報告
      boolean continuing =
          ackTypes.contains(FollowReasonType.PAIN_REPORT)
              && facts.painReports().stream().noneMatch(p -> p.getCreatedAt().isAfter(ackAt));
      if (facts.painDetailVisible()) {
        for (UserExcludedItem p : facts.painReports()) {
          conditions.add(
              new Condition(
                  FollowReasonType.PAIN_REPORT,
                  "痛みの報告（" + p.getItemName() + "を除外、理由「" + p.getReason().trim() + "」）",
                  continuing));
        }
      } else {
        conditions.add(new Condition(FollowReasonType.PAIN_REPORT, "痛みの報告（除外を追加）", continuing));
      }
    }

    Set<FollowReasonType> activeTypes = EnumSet.noneOf(FollowReasonType.class);
    conditions.forEach(c -> activeTypes.add(c.type()));

    long daysSinceAck = ack == null ? 0 : ChronoUnit.DAYS.between(ackAt.toLocalDate(), today);
    List<String> reasons = new ArrayList<>();
    for (Condition c : conditions) {
      if (!c.continuing()) {
        reasons.add(c.text());
      } else if (daysSinceAck >= REFLAG_AFTER_DAYS) {
        reasons.add(c.text() + "（前回の対応から" + daysSinceAck + "日）");
      }
    }

    if (!reasons.isEmpty()) {
      String label = ack == null ? null : "前回: 対応済み " + ackSummary(ack);
      return new Result(State.FLAGGED, reasons, activeTypes, label);
    }
    if (ack != null && (!conditions.isEmpty() || daysSinceAck < REFLAG_AFTER_DAYS)) {
      return new Result(
          State.ACKNOWLEDGED, List.of(), activeTypes, "対応済み（" + ackSummary(ack) + "）");
    }
    return new Result(State.NONE, List.of(), activeTypes, null);
  }

  /** 直近の終わった{@link #LOW_ACHIEVEMENT_WEEKS}週がすべて目標の半分未満なら理由の文言。目標なしの週・目標を設定した週を含む場合は判定しない。 */
  private static Optional<String> lowAchievementText(List<WeekResult> weeks) {
    if (weeks == null || weeks.size() < LOW_ACHIEVEMENT_WEEKS) return Optional.empty();
    List<WeekResult> recent = weeks.subList(weeks.size() - LOW_ACHIEVEMENT_WEEKS, weeks.size());
    for (WeekResult w : recent) {
      if (!w.evaluated() || w.target() <= 0 || w.doneDays() * 2 >= w.target()) {
        return Optional.empty();
      }
    }
    StringBuilder counts = new StringBuilder();
    for (WeekResult w : recent) {
      if (!counts.isEmpty()) counts.append("・");
      counts.append(w.doneDays()).append("回");
    }
    return Optional.of(LOW_ACHIEVEMENT_WEEKS + "週続けて目標の半分未満（" + counts + "）");
  }

  private static String ackSummary(MemberFollowAcknowledgement ack) {
    String name = ack.getActedByName() == null ? "" : " " + ack.getActedByName();
    return ack.getActedAt().format(MONTH_DAY) + name;
  }

  static Set<FollowReasonType> parseTypes(String csv) {
    Set<FollowReasonType> types = EnumSet.noneOf(FollowReasonType.class);
    if (csv == null || csv.isBlank()) return types;
    Arrays.stream(csv.split(","))
        .map(String::trim)
        .forEach(
            s -> {
              try {
                types.add(FollowReasonType.valueOf(s));
              } catch (IllegalArgumentException ignored) {
                // 将来削除した種類は無視する
              }
            });
    return types;
  }

  static String formatTypes(Set<FollowReasonType> types) {
    return String.join(",", types.stream().map(Enum::name).toList());
  }
}
