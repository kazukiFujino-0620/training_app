package com.example.traning.organization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "organizations")
@Data
public class Organization {

  /** 全組織共通を表す予約済みの特殊行（training_item_master等の共通マスタが参照する）。 */
  public static final long ALL_ORGANIZATION_ID = 0L;

  /** 既存データ・招待コード未入力の新規登録が割り当てられるデフォルト店舗（V17でシード）。 招待コードによる組織割り当て（フェーズ4）が実装されるまでの暫定値。 */
  public static final long DEFAULT_STORE_ORGANIZATION_ID = 2L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  /** {@link OrganizationType} の name()。 */
  private String type;

  @Column(name = "parent_organization_id")
  private Long parentOrganizationId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}
