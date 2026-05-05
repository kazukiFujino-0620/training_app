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
import com.example.traning.dao.training.TrainingDao;
import com.example.traning.dao.training.TrainingDetailDao;
import com.example.traning.entity.User;
import com.example.traning.repository.TrainingRepository;
import com.example.traning.training.entity.Training;
import com.example.traning.training.entity.TrainingDetail;

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

	// private final TrainingRepository trainingRepository;

	// public TrainingService(TrainingRepository trainingRepository) {
	// this.trainingRepository = trainingRepository;
	// }

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
					// Java 8のStreamを使うと1行で書けます
					// 「すべてのセットのisCompletedがtrueであること」を判定
					boolean allDone = training.getDetails().stream().allMatch(detail -> detail.isCompleted());

					currentDbData.setAllCompleted(allDone);
				} else {
					// セットが一つもない場合は未完了とする（またはお好みで）
					currentDbData.setAllCompleted(false);
				}
				// --------------------------------------

				trainingDao.update(currentDbData);
			}

			// 子データ（Details）の入れ替え処理はそのまま
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
		// DBからusernameをキーにIDを検索して返す処理
		// まだDaoがない場合は、暫定的に 1L を返すようにしてコンパイルを通すことも可能です
		Long userId = trainingDao.selectIdByUsername(username);

		if (userId == null) {
			return 1L; // 見つからない場合のセーフティ
		}
		return userId;
	}

	public List<TrainingDetail> findByDate(String date) {
		return trainingDetailDao.selectByDate(date);
	}

	public User getUserByName(Long userId) {
		return trainingDao.selectByUserName(userId);

	}

	public Map<String, Object> makeChartDataCustom(Long userId, String startStr, String endStr) {
		LocalDate start = LocalDate.parse(startStr);
		LocalDate end = LocalDate.parse(endStr);

		// 指定された期間の全日付ラベル(yyyy-MM-dd)を生成
		List<String> labels = new ArrayList<>();
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			labels.add(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		}

		Map<String, Object> chartData = new HashMap<>();
		chartData.put("labels", labels);

		// 各部位のデータを取得（ゼロ埋め処理込み）
		chartData.put("chest", getSafeVolumeData(userId, "CHEST", labels, startStr, endStr));
		chartData.put("back", getSafeVolumeData(userId, "BACK", labels, startStr, endStr));
		chartData.put("arms", getSafeVolumeData(userId, "ARM", labels, startStr, endStr));
		chartData.put("shoulders", getSafeVolumeData(userId, "SHOULDER", labels, startStr, endStr));
		chartData.put("legs", getSafeVolumeData(userId, "LEG", labels, startStr, endStr));

		return chartData;
	}

	// 2. 部位ごとのボリュームデータを取得し、存在しない日付を 0.0 で埋める
	private List<Double> getSafeVolumeData(Long userId, String partCode, List<String> labels, String startStr,
			String endStr) {
		// DBから期間内のデータリストを取得
		List<TrainingDao.VolumeResult> list = trainingDao.selectVolumeList(userId, partCode, startStr, endStr);

		// リストをMapに変換（日付をキーにして検索しやすくする）
		Map<String, Double> dbData = list.stream()
				.collect(java.util.stream.Collectors.toMap(
						res -> res.trainingDate,
						res -> res.totalVolume,
						(v1, v2) -> v1));

		List<Double> result = new ArrayList<>();
		// Java側で生成した labels (yyyy-MM-dd) に基づいてデータを詰める
		for (String label : labels) {
			// DB側の日付フォーマット(yyyy-MM-dd)と完全一致すればその値を、なければ 0.0
			result.add(dbData.getOrDefault(label, 0.0));
		}
		return result;
	}
}