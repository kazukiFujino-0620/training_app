package com.example.traning.repository;

import com.example.traning.training.Training;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingRepository extends JpaRepository<Training, Long> {
  // userオブジェクトの中の id を使って検索する
  List<Training> findByUserId(Long userId);
}
