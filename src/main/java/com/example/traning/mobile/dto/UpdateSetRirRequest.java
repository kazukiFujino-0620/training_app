package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * セットのRIR（余力）の更新リクエスト（機能見直し-1-#18）。
 *
 * <p>{@link UpdateSetRequest} は「nullは更新スキップ」の方式のため、未入力に戻す（null）操作を表せない。RIRは選び直し・未入力に戻すことが
 * あるため専用のリクエストにし、nullを送ると未入力に戻す。
 */
@Data
public class UpdateSetRirRequest {

  /** 0〜5。5は「5回以上」。nullは未入力（スキップ）。 */
  @Min(value = 0, message = "RIRは0〜5で指定してください")
  @Max(value = 5, message = "RIRは0〜5で指定してください")
  private Integer rir;
}
