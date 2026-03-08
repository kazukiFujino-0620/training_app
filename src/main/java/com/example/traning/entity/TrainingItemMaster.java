package com.example.traning.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue; // これを追加
import org.seasar.doma.GenerationType; // これを追加
import org.seasar.doma.Id; // これを追加
import org.seasar.doma.Table;

import lombok.Data;

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
}