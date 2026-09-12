package com.example.traning.template;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.Transient;

@Entity
@Table(name = "training_templates")
@Data
public class TrainingTemplate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  private String name;

  @Column(name = "part_code")
  private String partCode;

  private String memo;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Transient private List<TrainingTemplateItem> items;

  /**
   * itバグ-13: 一覧表示用に{@link #items}（1セット=1行のフラットな配列）を種目名ごとにグループ化する。 {@code items}が{@code
   * display_order}・{@code set_number}順に並んでいる前提のため、 呼び出し前に{@link
   * TrainingTemplateItemDao#selectByTemplateId}等でソート済みの結果を設定しておくこと。
   */
  public List<ItemGroup> getItemGroups() {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    Map<String, List<TrainingTemplateItem>> byItemName = new LinkedHashMap<>();
    for (TrainingTemplateItem item : items) {
      byItemName.computeIfAbsent(item.getItemName(), k -> new ArrayList<>()).add(item);
    }
    return byItemName.entrySet().stream()
        .map(e -> new ItemGroup(e.getKey(), e.getValue()))
        .toList();
  }

  /** 種目名と、その種目に属するセット一覧（{@link #getItemGroups()}の1件分）。 */
  public record ItemGroup(String itemName, List<TrainingTemplateItem> sets) {}
}
