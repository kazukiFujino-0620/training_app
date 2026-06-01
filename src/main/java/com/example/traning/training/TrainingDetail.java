package com.example.traning.training;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
	@NotNull(message = "セット番号は必須です")
	@Min(value = 1, message = "セット番号は1以上である必要があります")
	private Integer setNumber;

	@NotNull(message = "重量は必須です")
	@Min(value = 0, message = "重量は0以上である必要があります")
	private Double weight;

	@NotNull(message = "回数は必須です")
	@Min(value = 0, message = "回数は0以上である必要があります")
	private Integer reps;
	@Column(name = "count")
	private Integer count;

	@Column(name = "set_type")
	private String setType = "MAIN";

	@Column(name = "is_completed")
	@JsonProperty("completed")
	private boolean isCompleted;

	@Column(name = "create_datetime")
	private LocalDateTime createDatetime = LocalDateTime.now();
	@Column(name = "updated_datetime")
	private LocalDateTime updatedDatetime = LocalDateTime.now();
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@org.seasar.doma.Transient
	@ManyToOne
	@JoinColumn(name = "training_id", insertable = false, updatable = false)
	private Training training;

	public void setIsCompleted(boolean completed) {
		this.isCompleted = completed;
	}

	public boolean getIsCompleted() {
		return this.isCompleted;
	}
}
