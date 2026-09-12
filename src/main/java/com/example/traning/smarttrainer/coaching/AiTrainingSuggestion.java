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

/** AIトレーニング提案の週次キャッシュ（ita5-1 機能1）。1ユーザー・1週（月曜始まり）につき1件、7日分をまとめて保持する。 */
@Entity
@Table(name = "ai_training_suggestions")
@Data
public class AiTrainingSuggestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  /** 対象週の月曜日（ISO週）。 */
  @Column(name = "week_start_date")
  private LocalDate weekStartDate;

  /** {@link AiSuggestedDay}のリスト（7日分）をJSONシリアライズしたもの。 */
  @Column(name = "items_json")
  private String itemsJson;

  private String source;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();
}
