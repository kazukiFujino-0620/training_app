package com.example.traning.smarttrainer.coaching;

/**
 * ita5-1 機能2: トレーナーアドバイスのAI下書き。トレーナーの「AIで下書き」ボタン押下時のみ呼び出される
 * （自動生成はしない）。生成結果はあくまで下書きであり、送信可否・編集はトレーナーの判断に委ねる。
 */
public interface AdviceCoach {

  String generateDraft(AdviceContext context);

  String source();
}
