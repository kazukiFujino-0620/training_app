package com.example.traning.audit;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "audit_logs")
@Data
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "action")
  private String action;

  @Column(name = "target_table")
  private String targetTable;

  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "request_path")
  private String requestPath;

  @Column(name = "changed_at")
  private LocalDateTime changedAt = LocalDateTime.now();

  @Column(name = "extra")
  private String extra;
}
