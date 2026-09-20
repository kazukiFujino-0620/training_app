package com.example.traning.mobile.dto;

import com.example.traning.entity.TrainingItemFormCaution;
import com.example.traning.entity.TrainingItemFormGuide;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 種目フォーム解説のモバイルAPIレスポンス。機能見直し-1-#2。 */
@Data
@AllArgsConstructor
public class FormGuideResponse {

  private String itemName;
  private String imageUrl;
  private String videoUrl;
  private String jointAngleNote;
  private List<CautionItem> cautions;

  public static FormGuideResponse from(
      TrainingItemFormGuide guide, List<TrainingItemFormCaution> cautions) {
    List<CautionItem> cautionItems =
        cautions.stream()
            .map(c -> new CautionItem(c.getTitle(), c.getDescription(), c.getReason()))
            .toList();
    return new FormGuideResponse(
        guide.getItemName(),
        guide.getImageUrl(),
        guide.getVideoUrl(),
        guide.getJointAngleNote(),
        cautionItems);
  }

  @Data
  @AllArgsConstructor
  public static class CautionItem {
    private String title;
    private String description;
    private String reason;
  }
}
