package com.example.traning.mobile.dto;

import com.example.traning.smarttrainer.autogen.AutogenRules;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** メニュー自動作成（機能見直し-1-#4）のモバイルAPIリクエスト。レスポンスは WorkoutGenerationViews をそのまま返す。 */
public final class MobileWorkoutGenerationDtos {

  private MobileWorkoutGenerationDtos() {}

  @Data
  public static class GenerateRequest {
    @NotNull(message = "使える時間を指定してください")
    @Min(value = AutogenRules.MIN_AVAILABLE_MINUTES, message = "使える時間は15分以上で指定してください")
    @Max(value = AutogenRules.MAX_AVAILABLE_MINUTES, message = "使える時間が長すぎます")
    private Integer availableMinutes;

    /** 対象日（未指定は今日）。過去日は不可。 */
    private LocalDate date;

    /** 指定順が優先度。未指定・空ならおまかせ（またはプログラムどおり）。 */
    @Size(max = 10, message = "部位の指定が多すぎます")
    private List<String> desiredPartCodes;

    /** 使える器具のID。未指定なら器具で絞り込まない。 */
    @Size(max = 100, message = "器具の指定が多すぎます")
    private List<Long> availableEquipmentIds;

    /** その場限りの除外（保存済みの除外と合わせて使う）。 */
    @Size(max = 100, message = "除外の指定が多すぎます")
    private List<String> excludeItemNames;
  }

  @Data
  public static class AddExclusionRequest {
    @NotBlank(message = "種目を選んでください")
    @Size(max = 100, message = "種目名が長すぎます")
    private String itemName;

    @Size(max = AutogenRules.EXCLUSION_REASON_MAX_LENGTH, message = "理由は200文字以内で入力してください")
    private String reason;
  }
}
