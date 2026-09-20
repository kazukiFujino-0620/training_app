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
 * 種目のフォーム解説（画像/動画＋関節角度の注記）。機能見直し-1-#2。
 *
 * <p>{@code item_name} で {@link TrainingItemMaster#getItemName()} と文字列一致で紐付ける
 * （user_item_rest_preferences と同じ既存パターンを踏襲。IDでのFKにしないのは組織固有種目の CSV再取込でIDが変わり得るため）。
 */
@Entity
@Table(name = "training_item_form_guides")
@Data
public class TrainingItemFormGuide {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "image_url")
  private String imageUrl;

  @Column(name = "video_url")
  private String videoUrl;

  @Column(name = "joint_angle_note")
  private String jointAngleNote;

  @Column(name = "created_at")
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt = LocalDateTime.now();
}
