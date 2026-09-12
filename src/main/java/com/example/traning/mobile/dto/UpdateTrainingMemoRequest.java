package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** モバイル: トレーニングメモ更新API（PATCH /api/mobile/training/{id}/memo）のリクエスト（ita4-4）。 */
@Data
public class UpdateTrainingMemoRequest {

  @Size(max = 500, message = "メモは500文字以内で入力してください")
  private String memo;
}
