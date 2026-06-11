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
}
