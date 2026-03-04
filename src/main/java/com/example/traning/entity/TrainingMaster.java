package com.example.traning.entity;

import org.seasar.doma.Entity;
import org.seasar.doma.Table;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@Table(name = "trainings")
@Data // Getter/Setterの自動生成
public class TrainingMaster {

	private String partCode;
	private String partName;
	private Integer displayOrder;
}
