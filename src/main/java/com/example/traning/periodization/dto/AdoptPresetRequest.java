package com.example.traning.periodization.dto;

import jakarta.validation.constraints.NotNull;

/** プリセット採用リクエスト（Web用）。 */
public record AdoptPresetRequest(@NotNull Long presetProgramId) {}
