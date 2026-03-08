package com.example.traning.training.entity;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.Data;

@Entity
@Table(name = "training_details")
@Data
public class TrainingDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)

	private Long id;

	@Column(name = "training_id")
	private Long trainingId;

	@Column(name = "set_number")
	private Integer setNumber;
	private Double weight;
	private Integer reps;

	@Column(name = "is_completed")
	private boolean isCompleted;

	@Column(name = "create_datetime")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_datetime")
	private LocalDateTime updatedDatetime = LocalDateTime.now();
}
