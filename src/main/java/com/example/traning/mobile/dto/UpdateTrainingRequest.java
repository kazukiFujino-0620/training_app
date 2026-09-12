package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import lombok.Data;

/**
 * モバイル: トレーニング本体（種目名・部位・日付）更新API（PATCH /api/mobile/training/{id}）のリクエスト（ita7-1）。
 *
 * <p>セット（重量・回数・完了状態）は既存の {@code PATCH /api/mobile/training/sets/{id}} で別途管理するため、ここでは扱わない。
 */
@Data
public class UpdateTrainingRequest {

  @NotBlank(message = "種目名は必須です")
  private String menu;

  @NotBlank(message = "部位コードは必須です")
  @Pattern(regexp = "^(CHEST|BACK|LEG|SHOULDER|ARM|CARDIO)$", message = "部位コードが不正です")
  private String partCode;

  @NotNull(message = "日付は必須です")
  private LocalDate trainingDate;
}
