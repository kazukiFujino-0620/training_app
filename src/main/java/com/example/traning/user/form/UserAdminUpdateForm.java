package com.example.traning.user.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理者によるユーザー情報更新専用フォーム。 password を除いた安全なフィールドのみバインドを許可する。 role は ROLE_USER/ROLE_ADMIN の2値のみ許可し（Mass
 * Assignment対策として値を限定）、 ORG_ADMIN/STORE_ADMIN 等の組織権限はこのエンドポイントでは変更不可。
 */
@Data
public class UserAdminUpdateForm {

  @NotNull private Integer userId;

  @NotBlank
  @Size(max = 50)
  private String userName;

  @NotBlank
  @Pattern(regexp = "ROLE_USER|ROLE_ADMIN", message = "権限の値が不正です")
  private String role;

  @NotNull private Boolean enabled;
}
