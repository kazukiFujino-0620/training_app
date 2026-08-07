package com.example.traning.smarttrainer.recommendation;

/** 推奨種目1件分（重量・回数レンジ、セット数）。 */
public record RecommendedItem(
    String itemName, double weightMin, double weightMax, int repsMin, int repsMax, int sets) {}
