package com.example.traning.periodization.dto;

import com.example.traning.periodization.ProposalResponse;
import jakarta.validation.constraints.NotNull;

/** トレーナーの案に対するトレーニーの選択（Web用）。 */
public record RespondProposalRequest(@NotNull ProposalResponse response) {}
