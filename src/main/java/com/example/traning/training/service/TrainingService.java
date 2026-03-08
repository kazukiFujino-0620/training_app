package com.example.traning.training.service;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.training.TrainingDao;
import com.example.traning.dao.training.TrainingDetailDao;
import com.example.traning.training.entity.Training;

@Service
public class TrainingService {
	@Autowired
	private TrainingServiceTransaction transaction;
	@Autowired
	private TrainingDao trainingDao;
	@Autowired
	private TrainingDetailDao trainingDetailDao;

	public void save(Training training, Principal principal) {
		// 1. 入力値チェック (バリデーション)
		if (training.getMenu() == null || training.getMenu().isEmpty()) {
			throw new IllegalArgumentException("種目を選択してください");
		}
		if (training.getDetails() == null || training.getDetails().isEmpty()) {
			throw new IllegalArgumentException("セット内容を入力してください");
		}

		// 2. トランザクション処理の呼び出し
		transaction.execute(training, principal);
	}

	@Transactional
	public void deleteTraining(Long id) {
		trainingDetailDao.deleteByTrainingId(id);

		Training training = trainingDao.selectById(id);
		if (training != null) {
			trainingDao.delete(training);
		}
	}
}