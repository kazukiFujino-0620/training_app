package com.example.traning.training;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "training_details")
@Table(name = "training_details")
@Data
public class TrainingDetail {

	@Id
	@jakarta.persistence.Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
	private Long id;

	@Column(name = "training_id")
	@jakarta.persistence.Transient
	private Long trainingId;

	@Column(name = "set_number")
	private Integer setNumber;
	private Double weight;
	private Integer reps;
	@Column(name = "count")
	private Integer count;

	@Column(name = "is_completed")
	private boolean isCompleted;

	@Column(name = "create_datetime")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_datetime")
	private LocalDateTime updatedDatetime = LocalDateTime.now();

	@org.seasar.doma.Transient
	@ManyToOne
	@JoinColumn(name = "training_id", insertable = false, updatable = false)
	private Training training;
}
