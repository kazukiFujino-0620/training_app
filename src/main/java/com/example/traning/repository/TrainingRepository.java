package com.example.traning.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.traning.training.entity.Training;

public interface TrainingRepository extends JpaRepository<Training, Long> {
	// userオブジェクトの中の id を使って検索する
	List<Training> findByUserId(Long userId);
}
