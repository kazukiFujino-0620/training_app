package com.example.traning.smarttrainer.coaching;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.training.Training;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ita5-1 機能2のmock実装。GCPテスト環境では実際の外部AI（Claude Haiku 4.5）を呼び出さず、
 * トレーニーの対象日のトレーニング内容・疲労度・推奨メニューを元にした簡易的な下書き文を返す。
 * 本番環境構築時に実API連携へ差し替える。生成結果はあくまで下書きであり、送信前にトレーナーが必ず確認・編集する想定。
 */
@Component
public class MockAdviceCoach implements AdviceCoach {

  private static final String SOURCE = "mock";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d");
  private static final Map<String, String> PART_LABEL_MAP =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  @Override
  public String generateDraft(AdviceContext context) {
    StringBuilder sb = new StringBuilder();
    sb.append("（モック下書き）").append(context.traineeName()).append("さん\n\n");
    sb.append(context.targetDate().format(DATE_FORMATTER)).append("のトレーニング、お疲れ様でした。");

    List<Training> trainings = context.targetDateTrainings();
    if (trainings.isEmpty()) {
      sb.append("この日は記録がないようですが、無理のない範囲で継続していきましょう。");
    } else {
      sb.append(trainings.size()).append("種目に取り組まれていましたね。");
      boolean allCompleted = trainings.stream().allMatch(Training::isAllCompleted);
      sb.append(allCompleted ? "最後までやり切れていて素晴らしいです。" : "無理せず自分のペースで進めていきましょう。");
    }

    String mostFatiguedLabel = mostFatiguedPartLabel(context);
    if (mostFatiguedLabel != null) {
      sb.append("\n\n").append(mostFatiguedLabel).append("の疲労度がやや高めなので、この後は回復を意識してみてください。");
    }

    if (!context.recommendation().restDayRecommended()
        && context.recommendation().partLabel() != null) {
      sb.append("\n\n次回は")
          .append(context.recommendation().partLabel())
          .append("を中心に組んでいくのがおすすめです。");
    }

    List<PersonalRecord> prs = context.personalRecords();
    if (!prs.isEmpty()) {
      sb.append("\n\n引き続き自己ベスト更新を目指して頑張りましょう！");
    }

    return sb.toString();
  }

  @Override
  public String source() {
    return SOURCE;
  }

  private String mostFatiguedPartLabel(AdviceContext context) {
    Map<String, Integer> fatiguePct = context.fatigueResult().fatiguePct();
    String mostFatiguedPart = null;
    int maxPct = -1;
    for (Map.Entry<String, Integer> entry : fatiguePct.entrySet()) {
      if (entry.getValue() != null && entry.getValue() > maxPct) {
        maxPct = entry.getValue();
        mostFatiguedPart = entry.getKey();
      }
    }
    if (mostFatiguedPart == null || maxPct < 60) {
      return null;
    }
    return PART_LABEL_MAP.getOrDefault(mostFatiguedPart, mostFatiguedPart);
  }
}
