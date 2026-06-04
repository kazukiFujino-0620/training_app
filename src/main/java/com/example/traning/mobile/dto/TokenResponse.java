package com.example.traning.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponse {

	private String accessToken;
	private String refreshToken;
	/** アクセストークンの有効期限（秒） */
	private long expiresIn;
}
