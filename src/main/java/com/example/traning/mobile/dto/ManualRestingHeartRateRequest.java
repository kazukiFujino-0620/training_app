package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** モバイル: 安静時心拍の手動入力（PUT /api/mobile/health/resting-heart-rate）のリクエスト（機能見直し-1-#5）。 */
@Data
public class ManualRestingHeartRateRequest {

  @NotNull(message = "日付は必須です")
  private LocalDate date;

  /** 範囲の判定はサービス側（HealthInputLimits）で行う。 */
  @NotNull(message = "安静時心拍は必須です")
  private Integer restingBpm;

  /** 確認範囲の値を、利用者が確認表示を見たうえで保存する場合にtrue。 */
  private boolean confirmed;
}
