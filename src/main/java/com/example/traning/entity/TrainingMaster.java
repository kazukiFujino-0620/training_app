package com.example.traning.entity;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Table;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@Table(name = "training_part_master")
@Data // Getter/Setterの自動生成
public class TrainingMaster {

	@Column(name = "part_code")
	private String partCode;
	@Column(name = "part_Name")
	private String partName;
	@Column(name = "display_order")
	private Integer displayOrder;
}
