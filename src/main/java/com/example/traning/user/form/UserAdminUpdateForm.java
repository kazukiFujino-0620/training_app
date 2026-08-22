package com.example.traning.user.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 管理者によるユーザー情報更新専用フォーム。 password を除いた安全なフィールドのみバインドを許可する。
 *
 * <p>role は ROLE_USER/ROLE_ADMIN/ROLE_ORG_ADMIN/ROLE_STORE_ADMIN の4値を許可する（ita1-1
 * 未実施分対応）。実際にどのロールへ変更できるかは操作者の権限に応じてサービス層（{@code AdminController#updateUser}）で判定し、
 * ここでのバリデーションは値の形式チェックのみ。organizationId・storeAssignmentsも同様に、実際の反映可否は サービス層の権限チェックに委ねる（Mass
 * Assignment対策として値は限定するが、範囲チェックはここでは行わない）。
 */
@Data
public class UserAdminUpdateForm {

  @NotNull private Integer userId;

  @NotBlank
  @Size(max = 50)
  private String userName;

  @NotBlank
  @Pattern(regexp = "ROLE_USER|ROLE_ADMIN|ROLE_ORG_ADMIN|ROLE_STORE_ADMIN", message = "権限の値が不正です")
  private String role;

  @NotNull private Boolean enabled;

  /** 所属組織のorganization_id。ROLE_ADMINが操作する場合のみ実際に反映される。 */
  private Long organizationId;

  /** 店舗兼任先のorganization_id（STORE）一覧。roleがROLE_STORE_ADMINの場合のみ意味を持つ。 */
  private List<Long> storeAssignments;
}
