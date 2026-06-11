package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

  @Email @NotBlank private String email;

  @NotBlank private String password;

  /** デバイス固有ID（UUIDなど）。リフレッシュトークンの紐付けに使用。 */
  @NotBlank private String deviceId;
}
