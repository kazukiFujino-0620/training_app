package com.example.traning.mobile.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.mobile.dto.UpdateSetRequest;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/training")
public class MobileTrainingController {

	private final TrainingService trainingService;
	private final TrainingDao trainingDao;
	private final TrainingDetailDao trainingDetailDao;

	public MobileTrainingController(TrainingService trainingService,
			TrainingDao trainingDao,
			TrainingDetailDao trainingDetailDao) {
		this.trainingService = trainingService;
		this.trainingDao = trainingDao;
		this.trainingDetailDao = trainingDetailDao;
	}

	/**
	 * 当日（またはdate指定日）のトレーニング一覧を返す。
	 * 各Trainingにdetailsリスト（セット情報）が含まれる。
	 */
	@GetMapping("/today")
	public ResponseEntity<List<Training>> getToday(
			@AuthenticationPrincipal Long userId,
			@RequestParam(required = false) String date) {

		LocalDate targetDate = (date != null) ? LocalDate.parse(date) : LocalDate.now();
		List<Training> trainings = trainingService.getFullTrainingData(userId, targetDate);
		return ResponseEntity.ok(trainings);
	}

	/**
	 * セット完了フラグを更新する。
	 * リクエストボディ: {"completed": true/false}
	 */
	@PutMapping("/sets/{id}/complete")
	@Transactional
	public ResponseEntity<Void> completeSet(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@RequestBody java.util.Map<String, Boolean> body) {

		TrainingDetail detail = trainingDetailDao.selectById(id);
		if (detail == null) {
			return ResponseEntity.notFound().build();
		}
		if (!isOwnedByUser(detail, userId)) {
			return ResponseEntity.status(403).build();
		}

		Boolean completed = body.get("completed");
		if (completed == null) {
			return ResponseEntity.badRequest().build();
		}

		detail.setIsCompleted(completed);
		detail.setUpdatedDatetime(java.time.LocalDateTime.now());
		trainingDetailDao.update(detail);
		return ResponseEntity.noContent().build();
	}

	/**
	 * セットの重量・回数を更新する。
	 */
	@PutMapping("/sets/{id}/update")
	@Transactional
	public ResponseEntity<Void> updateSet(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long id,
			@Valid @RequestBody UpdateSetRequest req) {

		TrainingDetail detail = trainingDetailDao.selectById(id);
		if (detail == null) {
			return ResponseEntity.notFound().build();
		}
		if (!isOwnedByUser(detail, userId)) {
			return ResponseEntity.status(403).build();
		}

		if (req.getWeight() != null) detail.setWeight(req.getWeight());
		if (req.getReps() != null) {
			detail.setReps(req.getReps());
			detail.setCount(req.getReps());
		}
		detail.setUpdatedDatetime(java.time.LocalDateTime.now());
		trainingDetailDao.update(detail);
		return ResponseEntity.noContent().build();
	}

	/** detailの親TrainingがuserId所有かを確認 */
	private boolean isOwnedByUser(TrainingDetail detail, Long userId) {
		Training training = trainingDao.selectById(detail.getTrainingId());
		return training != null && userId.equals(training.getUserId());
	}
}
