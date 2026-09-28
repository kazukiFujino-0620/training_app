package com.example.traning.periodization.dto;

import jakarta.validation.constraints.NotNull;

/** トレーナーが案を送るリクエスト（Web用）。 */
public record SendProposalRequest(@NotNull Long traineeUserId, @NotNull Long presetProgramId) {}
