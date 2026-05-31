package com.example.traning.training.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionRecord {
    private String date;
    private List<SetRecord> sets;
}
