package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

	@NotBlank
	private String refreshToken;

	@NotBlank
	private String deviceId;
}
