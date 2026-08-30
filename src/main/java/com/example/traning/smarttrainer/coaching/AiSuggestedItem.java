package com.example.traning.smarttrainer.coaching;

/**
 * AIトレーニング提案の種目1件分（重量・回数レンジ、セット数）。{@link
 * com.example.traning.smarttrainer.recommendation.RecommendedItem}と同じ形。
 */
public record AiSuggestedItem(
    String itemName, double weightMin, double weightMax, int repsMin, int repsMax, int sets) {}
