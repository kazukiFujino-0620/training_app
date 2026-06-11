package com.example.traning.withdrawal;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity
@Table(name = "withdrawal_requests")
@Data
public class WithdrawalRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "user_id")
  public Long userId;

  @Column(name = "reason_type")
  public String reasonType;

  @Column(name = "reason_text")
  public String reasonText;

  public String status;

  @Column(name = "requested_at")
  public LocalDateTime requestedAt;

  @Column(name = "processed_at")
  public LocalDateTime processedAt;

  @Column(name = "processed_by")
  public Long processedBy;

  @Column(name = "created_at")
  public LocalDateTime createdAt;

  @Column(name = "updated_at")
  public LocalDateTime updatedAt;
}
