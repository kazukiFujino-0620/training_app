package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddSetRequest {

	@NotNull(message = "重量は必須です")
	@Min(value = 0, message = "重量は0以上である必要があります")
	private Double weight;

	@NotNull(message = "回数は必須です")
	@Min(value = 0, message = "回数は0以上である必要があります")
	private Integer reps;

	/** WARMUP / MAIN / DROPSET など。未指定時は MAIN */
	private String setType = "MAIN";
}
