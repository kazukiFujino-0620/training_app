package com.example.traning.organization;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 店舗兼任（ある店舗管理者/トレーナーが自店舗以外の店舗にもアクセスできる権限）を表す。 */
@Entity
@Table(name = "user_store_access")
@Data
public class UserStoreAccess {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "store_organization_id")
  private Long storeOrganizationId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;
}
