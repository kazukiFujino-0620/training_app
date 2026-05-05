package com.example.traning.training.service;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;

@Service
public class TrainingServiceTransaction {
	@Autowired
	private TrainingDao trainingDao;
	@Autowired
	private TrainingDetailDao trainingDetailDao;

	@Transactional
	public void execute(Training training, Principal principal) {
		if (training.getId() == null) {
			trainingDao.insert(training);
		} else {
			trainingDetailDao.deleteByTrainingId(training.getId());
			trainingDao.update(training);
		}

		for (int i = 0; i < training.getDetails().size(); i++) {
			TrainingDetail d = training.getDetails().get(i);
			d.setTrainingId(training.getId());
			d.setSetNumber(i + 1);
			trainingDetailDao.insert(d);
		}
	}
}
