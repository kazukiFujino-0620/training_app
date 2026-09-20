package com.example.traning.entity;

import java.time.LocalDateTime;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

/**
 * 種目の「よくある誤り」注意事項1件分。機能見直し-1-#2。
 *
 * <p>1種目につき2〜3件、{@code display_order} 昇順で表示する。{@code item_name} で {@link
 * TrainingItemMaster#getItemName()} と文字列一致で紐付ける（training_item_form_guides と同じ方針）。
 */
@Entity
@Table(name = "training_item_form_cautions")
@Data
public class TrainingItemFormCaution {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "display_order")
  private Integer displayOrder = 1;

  /** よくある誤りの通称。 */
  @Column(name = "title")
  private String title;

  /** 現象説明。 */
  @Column(name = "description")
  private String description;

  /** 一般的に指摘されている理由（中立的な言い回しで記載）。 */
  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();
}
