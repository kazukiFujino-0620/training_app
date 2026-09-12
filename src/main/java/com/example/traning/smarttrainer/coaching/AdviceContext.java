package com.example.traning.smarttrainer.coaching;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.training.Training;
import java.time.LocalDate;
import java.util.List;

/** ita5-1 機能2向けの下書き生成コンテキスト（トレーニーの直近状況）。 */
public record AdviceContext(
    String traineeName,
    LocalDate targetDate,
    List<Training> targetDateTrainings,
    DailyRecommendation recommendation,
    FatigueCalculator.FatigueResult fatigueResult,
    List<PersonalRecord> personalRecords) {}
