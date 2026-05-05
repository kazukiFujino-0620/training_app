package com.example.traning.training;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.Table;

import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Entity(immutable = false)
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "trainings")
@Table(name = "trainings")
@Data
public class Training {
	@org.seasar.doma.Id
	@jakarta.persistence.Id
	@org.seasar.doma.GeneratedValue(strategy = org.seasar.doma.GenerationType.IDENTITY)
	@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
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

	@org.seasar.doma.Transient
	@OneToMany(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TrainingDetail> details = new ArrayList<>();
	@org.seasar.doma.Transient
	@jakarta.persistence.Transient
	private String partName;
}
