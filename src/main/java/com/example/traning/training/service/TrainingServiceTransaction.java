package com.example.traning.training.service;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.training.TrainingDao;
import com.example.traning.dao.training.TrainingDetailDao;
import com.example.traning.training.entity.Training;
import com.example.traning.training.entity.TrainingDetail;

@Service
public class TrainingServiceTransaction {
	@Autowired
	private TrainingDao trainingDao;
	@Autowired
	private TrainingDetailDao trainingDetailDao;

	@Transactional
	public void execute(Training training, Principal principal) {
		// 親の保存
		if (training.getId() == null) {
			trainingDao.insert(training);
		} else {
			// 更新時は既存の明細を一度削除（洗替）
			trainingDetailDao.deleteByTrainingId(training.getId());
			trainingDao.update(training);
		}

		// 子（明細）の保存
		for (int i = 0; i < training.getDetails().size(); i++) {
			TrainingDetail d = training.getDetails().get(i);
			d.setTrainingId(training.getId());
			d.setSetNumber(i + 1);
			trainingDetailDao.insert(d);
		}
	}
}