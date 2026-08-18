package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateSetRequest {

  @Min(0)
  private Double weight;

  @Min(0)
  private Integer reps;

  private Boolean isCompleted;

  /** 有酸素運動（ita2-1）の実施時間（分）。開始〜完了のタイマーから自動反映。 */
  @Min(0)
  private Integer durationMin;

  /** 有酸素運動（ita2-1）の距離（km）。マシンの表示値を手入力。 */
  @Min(0)
  private Double distanceKm;

  /** 有酸素運動（ita2-1）の平均心拍数（bpm）。マシンの表示値を手入力。 */
  @Min(0)
  private Integer avgHeartRateBpm;

  /** 有酸素運動（ita2-1）の消費カロリー（kcal）。マシンの表示値を手入力。 */
  @Min(0)
  private Double caloriesKcal;
}
