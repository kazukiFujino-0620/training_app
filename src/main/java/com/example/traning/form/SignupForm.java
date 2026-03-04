package com.example.traning.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class SignupForm {

	@NotBlank
	private String username;

	@NotBlank
	@Email
	private String email;

	@NotBlank
	@Size(min = 8)
	private String password;

	@NotBlank
	@Size(min = 8)
	private String password_confirm;
	// Getter ,Setter

}
