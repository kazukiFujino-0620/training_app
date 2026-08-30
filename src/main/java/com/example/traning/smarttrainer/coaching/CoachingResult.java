package com.example.traning.smarttrainer.coaching;

import java.util.List;

/** {@link TrainingCoach}の生成結果。休養日推奨時はitemsが空リストになる。 */
public record CoachingResult(String comment, List<AiSuggestedItem> items) {}
