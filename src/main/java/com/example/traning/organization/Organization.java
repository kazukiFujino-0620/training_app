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

  /**
   * 招待コードなしで登録した一般ユーザー（ジム非所属）の受け皿となる組織（V17でシード、V37で「一般（未所属）」に改名）。
   * 既存データもこのIDに割り当てられている。
   */
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
