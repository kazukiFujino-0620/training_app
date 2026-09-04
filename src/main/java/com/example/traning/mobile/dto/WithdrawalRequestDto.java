package com.example.traning.mobile.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** モバイル: ジム所属ユーザーの退会申請（POST /api/mobile/withdrawal/request）のリクエスト。 */
@Data
public class WithdrawalRequestDto {

  private String reasonType;

  @Size(max = 500, message = "詳細は500文字以内で入力してください")
  private String reasonText;
}
