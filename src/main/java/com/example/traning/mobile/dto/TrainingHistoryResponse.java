package com.example.traning.mobile.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TrainingHistoryResponse {
  private String date;
  private List<SetRecord> sets;

  @Data
  @AllArgsConstructor
  public static class SetRecord {
    private int setNo;
    private double weight;
    private int reps;
  }
}
