package com.example.traning.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 当日の推定消費カロリー（ita2-3、力学的仕事量ベース）。計算不可の場合はavailable=false、caloriesはnull。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingCalorieResponse {
  private boolean available;
  private Integer calories;
}
