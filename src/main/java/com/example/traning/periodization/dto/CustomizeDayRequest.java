package com.example.traning.periodization.dto;

import jakarta.validation.constraints.NotNull;

/** 曜日→部位割当の変更リクエスト（Web用）。partCodeが空なら休養日。 */
public record CustomizeDayRequest(
    @NotNull Integer weekNumber, @NotNull String dayOfWeek, String partCode) {}
