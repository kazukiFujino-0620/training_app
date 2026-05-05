package com.example.traning.training.service;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.repository.TrainingRepository;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;

@Service
public class TrainingService {
	@Autowired
	private TrainingServiceTransaction transaction;
	@Autowired
	private TrainingDao trainingDao;
	@Autowired
	private TrainingDetailDao trainingDetailDao;
	@Autowired
	private TrainingMasterDao trainingMasterDao;
	@Autowired
	private TrainingRepository trainingRepository;

	public void save(Training training, Principal principal) {
		if (training.getMenu() == null || training.getMenu().isEmpty()) {
			throw new IllegalArgumentException("種目を選択してください");
		}
		if (training.getDetails() == null || training.getDetails().isEmpty()) {
			throw new IllegalArgumentException("セット内容を入力してください");
		}
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

	public List<Training> getFullTrainingData(Long userId, LocalDate date) {
		List<Training> trainingList = trainingDao.selectByDate(userId, date);

		for (Training t : trainingList) {
			t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
			t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
		}

		return trainingList;
	}

	public void deleteById(Long id) {
		this.deleteTraining(id);
	}

	@Transactional
	public void saveAll(List<Training> trainingList) {
		for (Training training : trainingList) {
			Training currentDbData = trainingDao.selectById(training.getId());

			if (currentDbData != null) {
				currentDbData.setMemo(training.getMemo());
				currentDbData.setUpdatedDatetime(LocalDateTime.now());
				currentDbData.setDuration(training.getDuration());

				if (training.getDetails() != null && !training.getDetails().isEmpty()) {
					boolean allDone = training.getDetails().stream().allMatch(detail -> detail.isCompleted());
					currentDbData.setAllCompleted(allDone);
				} else {
					currentDbData.setAllCompleted(false);
				}

				trainingDao.update(currentDbData);
			}

			trainingDetailDao.deleteByTrainingId(training.getId());
			if (training.getDetails() != null) {
				for (TrainingDetail detail : training.getDetails()) {
					detail.setTrainingId(training.getId());
					detail.setCompleted(detail.isCompleted());
					trainingDetailDao.insert(detail);
				}
			}
		}
	}

	public Long getUserIdByName(String username) {
		Long userId = trainingDao.selectIdByUsername(username);
		if (userId == null) {
			return 1L;
		}
		return userId;
	}

	public List<TrainingDetail> findByDate(String date) {
		return trainingDetailDao.selectByDate(date);
	}

	public List<TrainingDetail> findByUserIdAndDate(Long userId, String date) {
		return trainingDetailDao.selectByUserIdAndDate(userId, date);
	}

	public User getUserByName(Long userId) {
		return trainingDao.selectByUserName(userId);
	}

	public Map<String, Object> makeChartDataCustom(Long userId, String startStr, String endStr) {
		LocalDate start = LocalDate.parse(startStr);
		LocalDate end = LocalDate.parse(endStr);

		List<String> labels = new ArrayList<>();
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			labels.add(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		}

		Map<String, Object> chartData = new HashMap<>();
		chartData.put("labels", labels);
		chartData.put("chest", getSafeVolumeData(userId, "CHEST", labels, startStr, endStr));
		chartData.put("back", getSafeVolumeData(userId, "BACK", labels, startStr, endStr));
		chartData.put("arms", getSafeVolumeData(userId, "ARM", labels, startStr, endStr));
		chartData.put("shoulders", getSafeVolumeData(userId, "SHOULDER", labels, startStr, endStr));
		chartData.put("legs", getSafeVolumeData(userId, "LEG", labels, startStr, endStr));

		return chartData;
	}

	private List<Double> getSafeVolumeData(Long userId, String partCode, List<String> labels, String startStr,
			String endStr) {
		List<TrainingDao.VolumeResult> list = trainingDao.selectVolumeList(userId, partCode, startStr, endStr);

		Map<String, Double> dbData = list.stream()
				.collect(java.util.stream.Collectors.toMap(
						res -> res.trainingDate,
						res -> res.totalVolume,
						(v1, v2) -> v1));

		List<Double> result = new ArrayList<>();
		for (String label : labels) {
			result.add(dbData.getOrDefault(label, 0.0));
		}
		return result;
	}
}
