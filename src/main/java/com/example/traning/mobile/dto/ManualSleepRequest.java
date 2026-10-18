package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/**
 * モバイル: 睡眠時間の手動入力（PUT /api/mobile/health/sleep）のリクエスト（機能見直し-1-#5）。
 *
 * <p>手動入力は前夜の主な睡眠の時間のみ（寝た時刻・起きた時刻は受け付けない、昼寝は含めない）。画面の「時間・分」は端末側で合計分に換算して送る。
 * 範囲の判定はサービス側（HealthInputLimits）で行う。
 */
@Data
public class ManualSleepRequest {

  /** 起きた日（health_sleep.sleep_dateと同じ起床日基準）。 */
  @NotNull(message = "日付は必須です")
  private LocalDate date;

  /** 0（徹夜）も可。 */
  @NotNull(message = "睡眠時間は必須です")
  private Integer durationMinutes;

  /** 確認範囲の値を、利用者が確認表示を見たうえで保存する場合にtrue。 */
  private boolean confirmed;
}
