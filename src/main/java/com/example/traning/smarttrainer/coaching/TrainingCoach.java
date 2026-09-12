package com.example.traning.smarttrainer.coaching;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.pr.PersonalRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AIトレーニング提案（ita5-1 機能1）の生成インターフェース。実装は{@link MockTrainingCoach}（現在有効）。
 * 本番AI連携時は同インターフェースのClaude実装に差し替える想定（呼び出し元・キャッシュ・安全性チェックのロジックは変更不要）。
 *
 * <p>週頭（月曜）に1回、月〜日の7日分をまとめて生成する（案B踏襲、頻度のみ週次に変更）。「反映」操作自体は 従来どおり1日分ずつ行う（呼び出し元が該当日のみ抽出する）。
 */
public interface TrainingCoach {

  /**
   * 週開始日（月曜）から7日分の提案を生成する。
   *
   * @param weekStartDate 対象週の月曜日
   * @param masterItemsByPart 部位コードごとの種目マスタ一覧（{@link
   *     com.example.traning.smarttrainer.recommendation.FatigueCalculator#PART_ORDER}の各部位）
   * @param personalRecordsByItemName 種目名をキーとした既存PR（重量レンジの目安に使う。無い種目は含まれない）
   */
  List<AiSuggestedDay> generateWeeklyPlan(
      LocalDate weekStartDate,
      Map<String, List<TrainingItemMaster>> masterItemsByPart,
      Map<String, PersonalRecord> personalRecordsByItemName);

  /** このコーチの生成元識別子（DB保存用。例: "mock", "claude-haiku-4-5"）。 */
  String source();
}
