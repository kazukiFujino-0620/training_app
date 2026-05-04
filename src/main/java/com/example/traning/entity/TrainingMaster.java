package com.example.traning.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@Table(name = "training_part_master")
@Data // Getter/Setterの自動生成
public class TrainingMaster {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "part_code")
	private String partCode;
	@Column(name = "item_Name")
	private String itemName;
	@Column(name = "display_order")
	private Integer displayOrder;
}
