package com.example.traning.smarttrainer.coaching;

import java.util.List;

/** {@code /menu}・トレーニング登録画面での表示用DTO。 */
public record AiTrainingSuggestionView(String comment, String partCode, List<AiSuggestedItem> items) {}
