package com.example.traning.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class GoalForm {

    @NotBlank(message = "種目名を選択してください")
    private String itemName;

    @DecimalMin(value = "0.5", message = "目標重量は0.5kg以上で入力してください")
    @DecimalMax(value = "999.99", message = "目標重量は999.99kg以下で入力してください")
    private BigDecimal targetWeight;

    @DecimalMin(value = "1", message = "目標回数は1回以上で入力してください")
    @DecimalMax(value = "9999", message = "目標回数は9999回以下で入力してください")
    private Integer targetReps;

    @NotNull(message = "目標期日を入力してください")
    @Future(message = "目標期日は未来の日付で入力してください")
    private LocalDate targetDate;

    @Size(max = 500, message = "メモは500文字以内で入力してください")
    private String memo;
}
