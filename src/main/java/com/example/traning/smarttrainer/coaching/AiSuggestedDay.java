package com.example.traning.smarttrainer.coaching;

import java.time.LocalDate;
import java.util.List;

/** 週間AIトレーニング提案（ita5-1 機能1）のうち1日分。休養日推奨時は{@code partCode}/{@code partLabel}がnull、itemsが空リストになる。 */
public record AiSuggestedDay(
    LocalDate date,
    String partCode,
    String partLabel,
    String comment,
    List<AiSuggestedItem> items,
    boolean restDayRecommended) {}
