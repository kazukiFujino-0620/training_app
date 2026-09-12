package com.example.traning.organization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 招待コード（ita1-1 未実施分・ita3-3 招待コード方式の発行・管理基盤）。 */
@Entity
@Table(name = "invite_codes")
@Data
public class InviteCode {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "max_uses")
  private Integer maxUses;

  @Column(name = "used_count")
  private Integer usedCount;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
