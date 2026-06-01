package com.example.traning.training.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SetRecord {
    private Integer setNumber;
    private Double weight;
    private Integer reps;
    private String setType;
}
