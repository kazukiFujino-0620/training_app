package com.example.traning.smarttrainer.coaching;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/** 筋肉疲労度マップのAI解釈コメントの当日キャッシュ（ita5-1 機能3）。1ユーザー・1対象日につき1件。 */
@Entity
@Table(name = "ai_fatigue_comments")
@Data
public class AiFatigueComment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "target_date")
  private LocalDate targetDate;

  private String comment;

  private String source;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();
}
