package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RestPreferenceUpsertRequest {

  @NotNull @Min(10) @Max(600)
  private Integer restSeconds;
}
