package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PushRegisterRequest {

	@NotBlank(message = "FCMトークンは必須です")
	private String fcmToken;

	@NotBlank(message = "プラットフォームは必須です")
	@Pattern(regexp = "ios|android", message = "platform は ios または android を指定してください")
	private String platform;

	@NotBlank(message = "デバイスIDは必須です")
	private String deviceId;
}
