package com.example.traning.periodization.dto;

import com.example.traning.periodization.RenewChoice;
import jakarta.validation.constraints.NotNull;

/** サイクル終了後の選択リクエスト（Web用、QA Q3-7）。CHOOSE_NEW_PRESETのときのみpresetProgramId必須。 */
public record RenewCycleRequest(@NotNull RenewChoice choice, Long presetProgramId) {}
