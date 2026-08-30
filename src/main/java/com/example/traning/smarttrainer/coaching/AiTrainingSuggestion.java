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

/** AIトレーニング提案の当日キャッシュ（ita5-1 機能1）。1ユーザー・1対象日につき1件。 */
@Entity
@Table(name = "ai_training_suggestions")
@Data
public class AiTrainingSuggestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "part_code")
  private String partCode;

  private String comment;

  /** {@link AiSuggestedItem}のリストをJSONシリアライズしたもの。休養日推奨時は"[]"。 */
  @Column(name = "items_json")
  private String itemsJson;

  private String source;

  @Column(name = "created_at")
  private LocalDateTime createdAt;
}
