package com.example.traning.smarttrainer.autogen;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** ユーザーの自己申告による除外種目（機能見直し-1-#4、QA Q4-4）。 */
@Entity
@Table(name = "user_excluded_items")
@Data
public class UserExcludedItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "last_confirmed_at")
  private LocalDateTime lastConfirmedAt = LocalDateTime.now();

  @Column(name = "next_confirmation_due_at")
  private LocalDateTime nextConfirmationDueAt;
}
