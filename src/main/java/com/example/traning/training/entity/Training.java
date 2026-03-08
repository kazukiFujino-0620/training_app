package com.example.traning.training.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;
import org.seasar.doma.Transient;

import lombok.Data;

@Entity(immutable = false) // DomaのEntity
@Table(name = "trainings")
@Data // Getter/Setterの自動生成
public class Training {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)

	private Long id;

	@Column(name = "is_all_completed")
	private boolean isAllCompleted;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "training_date")
	private LocalDate trainingDate;

	@Column(name = "part_code")
	private String partCode;

	private String menu;

	@Column(name = "create_datetime")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_datetime")
	private LocalDateTime updatedDatetime = LocalDateTime.now();

	// Domaの自動処理からは外すが、画面からリストとして受け取る用
	@Transient
	private List<TrainingDetail> details = new ArrayList<>();

	@Transient
	private String partName;
}
