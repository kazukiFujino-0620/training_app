package com.example.traning.training.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Table;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "trainings")
@Table(name = "trainings")
@Data // Getter/Setterの自動生成
public class Training {
	@org.seasar.doma.Id // Doma用
	@jakarta.persistence.Id // ★JPA用
	@org.seasar.doma.GeneratedValue(strategy = org.seasar.doma.GenerationType.IDENTITY) // Doma用
	@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY) // JPA用
	private Long id;

	@Column(name = "is_all_completed")
	private boolean isAllCompleted;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "training_date")
	private LocalDate trainingDate;

	@Column(name = "part_code")
	private String partCode;

	@Column(name = "menu")
	private String menu;

	@Column(name = "memo")
	private String memo;

	@Column(name = "duration")
	private String duration;

	@Column(name = "create_datetime")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_datetime")
	private LocalDateTime updatedDatetime = LocalDateTime.now();

	// Domaの自動処理からは外すが、画面からリストとして受け取る用
	@org.seasar.doma.Transient
	@jakarta.persistence.Transient
	private List<TrainingDetail> details = new ArrayList<>();
	@org.seasar.doma.Transient
	@jakarta.persistence.Transient
	private String partName;

	@Column(name = "training_id")
	private Long trainingId;

	@Column(name = "set_number")
	private Integer setNumber;
	private Double weight;
	@Column(name = "reps")
	private Integer reps;
	@Column(name = "count")
	private Integer count;

	@Column(name = "is_completed")
	private boolean isCompleted;
}
