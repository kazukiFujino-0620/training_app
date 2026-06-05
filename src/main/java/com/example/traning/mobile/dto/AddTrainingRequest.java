package com.example.traning.mobile.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AddTrainingRequest {

	@NotBlank(message = "種目名は必須です")
	private String menu;

	@NotBlank(message = "部位コードは必須です")
	private String partCode;

	/** 未指定時は当日 */
	private LocalDate trainingDate;

	private String memo;

	@NotEmpty(message = "セットは1件以上必要です")
	@Valid
	private List<AddSetRequest> sets;
}
