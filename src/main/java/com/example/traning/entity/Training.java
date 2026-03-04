package com.example.traning.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@Table(name = "trainings")
@Data // Getter/Setterの自動生成
public class Training {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private LocalDate trainingDate;
	private String partCode;
	private String menu;
	private Double weight;
	private Integer reps;
	private Integer sets;
	private Integer totalWeight;
	private Integer calorie;
	private String memo;
	@Column(name = "created_at")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_at")
	private LocalDateTime updatedDatetime = LocalDateTime.now();
}
