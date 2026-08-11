package com.example.traning.entity;

import lombok.Data;
import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue; // これを追加
import org.seasar.doma.GenerationType; // これを追加
import org.seasar.doma.Id; // これを追加
import org.seasar.doma.Table;

@Entity
@Table(name = "training_item_master")
@Data
public class TrainingItemMaster {

  @Id // 主キーであることを明示
  @GeneratedValue(strategy = GenerationType.IDENTITY) // 自動採番の場合
  private Long id; // これを追加！

  @Column(name = "part_code")
  private String partCode;

  @Column(name = "item_name")
  private String itemName;

  @Column(name = "display_order")
  private Integer displayOrder;

  /** 種目活用フラグ（0:使用不可, 1:使用可能）。本日以降の新規トレーニング登録では1のもののみ表示する。 */
  @Column(name = "master_flg")
  private Integer masterFlg;

  /** 0=オールマイティ(全組織共通)。組織固有種目の場合はその組織（GYM単位）のid。 */
  @Column(name = "organization_id")
  private Long organizationId;
}
