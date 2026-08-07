package com.example.traning.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyRequest {

  @NotBlank(message = "MFA仮トークンは必須です")
  private String mfaTempToken;

  @NotBlank(message = "deviceIdは必須です")
  private String deviceId;

  /** TOTP 6桁コード（otp / backupCode どちらか一方必須） */
  private String otp;

  /** バックアップコード */
  private String backupCode;
}
