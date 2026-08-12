package com.example.traning.mobile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** HealthKit/Health Connectから取得した健康データの同期リクエスト（ita3-1）。読み取り専用連携のため、アプリ→サーバーの一方向。 */
@Data
public class HealthSyncRequest {

  @NotBlank(message = "sourceは必須です")
  @Pattern(
      regexp = "HEALTHKIT|HEALTH_CONNECT",
      message = "sourceはHEALTHKITまたはHEALTH_CONNECTを指定してください")
  private String source;

  @Valid private List<WeightRecord> weight;

  @Valid private List<StepsRecord> steps;

  @Valid private List<HeartRateRecord> heartRate;

  @Valid private List<CaloriesRecord> calories;

  @Valid private List<SleepRecord> sleep;

  @Data
  public static class WeightRecord {
    @NotNull(message = "dateは必須です")
    private LocalDate date;

    @NotNull(message = "weightKgは必須です")
    private Double weightKg;

    private Double bodyFatPct;
  }

  @Data
  public static class StepsRecord {
    @NotNull(message = "dateは必須です")
    private LocalDate date;

    @NotNull(message = "stepCountは必須です")
    private Integer stepCount;
  }

  @Data
  public static class HeartRateRecord {
    @NotNull(message = "dateは必須です")
    private LocalDate date;

    private Integer avgBpm;
    private Integer minBpm;
    private Integer maxBpm;
  }

  @Data
  public static class CaloriesRecord {
    @NotNull(message = "dateは必須です")
    private LocalDate date;

    private Double activeCalories;
    private Double totalCalories;
  }

  @Data
  public static class SleepRecord {
    @NotNull(message = "dateは必須です")
    private LocalDate date;

    @NotNull(message = "startTimeは必須です")
    private LocalDateTime startTime;

    @NotNull(message = "endTimeは必須です")
    private LocalDateTime endTime;

    @NotNull(message = "durationMinutesは必須です")
    private Integer durationMinutes;
  }
}
