package com.example.traning.goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.Data;

@Entity
@Table(name = "training_goals")
@Data
public class TrainingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "target_weight")
    private BigDecimal targetWeight;

    @Column(name = "target_reps")
    private Integer targetReps;

    @Column(name = "target_date")
    private LocalDate targetDate;

    private String status;

    private String memo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
