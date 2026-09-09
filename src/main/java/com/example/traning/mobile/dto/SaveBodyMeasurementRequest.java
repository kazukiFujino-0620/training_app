package com.example.traning.mobile.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** モバイル: 体重・体脂肪率の手動記録API（POST /api/mobile/body）のリクエスト（ita7-1）。 */
@Data
public class SaveBodyMeasurementRequest {

  @NotNull(message = "日付は必須です")
  private LocalDate measuredDate;

  @NotNull(message = "体重は必須です")
  @DecimalMin(value = "20.0", message = "体重は20〜300kgの範囲で入力してください")
  @DecimalMax(value = "300.0", message = "体重は20〜300kgの範囲で入力してください")
  private Double weightKg;

  @DecimalMin(value = "0.0", message = "体脂肪率は0〜60%の範囲で入力してください")
  @DecimalMax(value = "60.0", message = "体脂肪率は0〜60%の範囲で入力してください")
  private Double bodyFatPct;

  @Size(max = 200, message = "メモは200文字以内で入力してください")
  private String memo;
}
