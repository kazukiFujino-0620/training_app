package com.example.traning.goal;

public record GoalWithProgress(
    TrainingGoal goal,
    String effectiveStatus,
    Double currentWeight,
    Integer progressPct
) {}
