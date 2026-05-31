package com.example.traning.training.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreviousTrainingResponse {

    private List<SessionRecord> sessions;
    private PrRecord pr;

    @Data
    @AllArgsConstructor
    public static class PrRecord {
        private Double maxWeight;
        private Integer maxReps;
        private String achievedDate;
    }
}
