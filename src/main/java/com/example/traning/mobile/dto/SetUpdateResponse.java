package com.example.traning.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetUpdateResponse {

  private Long id;
  private boolean isCompleted;
  private boolean isPR;

  /** PR更新時のメッセージ。isPR=false の場合は null */
  private String prMessage;

  /**
   * 推奨インターバル秒数（F4）。
   * 重量 / 種目の自己ベスト重量の比率から算出（軽負荷60秒/中負荷90秒/高負荷180秒）。
   * 算出不可（PR未登録・未完了セット等）の場合は null。
   */
  private Integer recommendedIntervalSeconds;
}
