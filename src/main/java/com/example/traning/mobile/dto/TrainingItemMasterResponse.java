package com.example.traning.mobile.dto;

import com.example.traning.entity.TrainingItemMaster;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 種目マスタ一覧APIのモバイル向けレスポンス。機能見直し-1-#2で {@code hasFormGuide} を追加した。
 *
 * <p>既存の{@link TrainingItemMaster}エンティティ直返しから移行。フィールド自体は据え置き、
 * フォーム解説（training_item_form_guides）が登録済みの種目かどうかを一括判定した結果を追加する
 * （種目数だけ個別APIを呼ぶN+1を避けるため、呼び出し元でitem_name集合との突き合わせのみ行う）。
 */
@Data
@AllArgsConstructor
public class TrainingItemMasterResponse {

  private Long id;
  private String partCode;
  private String itemName;
  private Integer displayOrder;
  private Integer masterFlg;
  private Long organizationId;
  private BigDecimal rangeOfMotionM;
  private Boolean isCompound;

  /** training_item_form_guidesに該当行があればtrue（機能見直し-1-#2）。 */
  private boolean hasFormGuide;

  public static TrainingItemMasterResponse from(TrainingItemMaster item, boolean hasFormGuide) {
    return new TrainingItemMasterResponse(
        item.getId(),
        item.getPartCode(),
        item.getItemName(),
        item.getDisplayOrder(),
        item.getMasterFlg(),
        item.getOrganizationId(),
        item.getRangeOfMotionM(),
        item.getIsCompound(),
        hasFormGuide);
  }
}
