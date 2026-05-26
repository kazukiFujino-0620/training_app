package com.example.traning.user.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理者によるユーザー情報更新専用フォーム。
 * role / password を除いた安全なフィールドのみバインドを許可する。
 */
@Data
public class UserAdminUpdateForm {

    @NotNull
    private Integer userId;

    @NotBlank
    @Size(max = 50)
    private String userName;

    @NotNull
    private Boolean enabled;
}
