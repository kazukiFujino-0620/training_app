package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** モバイル: 週の回数目標の保存（PUT /api/mobile/frequency-goal）のリクエスト（機能見直し-1-#7）。 */
@Data
public class FrequencyGoalUpdateRequest {

  @NotNull(message = "週の回数目標は必須です")
  @Min(value = 1, message = "週の回数目標は1〜7回の範囲で指定してください")
  @Max(value = 7, message = "週の回数目標は1〜7回の範囲で指定してください")
  private Integer weeklyTargetCount;
}
