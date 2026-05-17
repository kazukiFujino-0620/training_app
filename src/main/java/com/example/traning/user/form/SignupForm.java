package com.example.traning.user.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class SignupForm {

	@NotBlank
	private String username;

	@Email
	private String email;

	private String password;

	private String password_confirm;

	private String googleId;

	private String lineId;

	// OAuth2経由の登録かどうかを判断するヘルパーメソッド
	public boolean isOAuth2Signup() {
		return (googleId != null && !googleId.isEmpty()) || (lineId != null && !lineId.isEmpty());
	}
}
