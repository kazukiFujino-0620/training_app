package com.example.traning.training.service;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;

@Service
public class TrainingService {
	private static final Logger logger = LoggerFactory.getLogger(TrainingService.class);

	private final TrainingServiceTransaction transaction;
	private final TrainingDao trainingDao;
	private final TrainingDetailDao trainingDetailDao;
	private final TrainingMasterDao trainingMasterDao;

	public TrainingService(TrainingServiceTransaction transaction, TrainingDao trainingDao,
			TrainingDetailDao trainingDetailDao, TrainingMasterDao trainingMasterDao) {
		this.transaction = transaction;
		this.trainingDao = trainingDao;
		this.trainingDetailDao = trainingDetailDao;
		this.trainingMasterDao = trainingMasterDao;
	}

	public void save(Training training, Principal principal) {
		logger.info("トレーニングデータ保存開始 - ユーザー: {}", principal != null ? principal.getName() : "unknown");

		try {
			if (training.getMenu() == null || training.getMenu().isEmpty()) {
				logger.error("トレーニング保存失敗: 種目が未選択");
				throw new IllegalArgumentException("種目を選択してください");
			}
			if (training.getDetails() == null || training.getDetails().isEmpty()) {
				logger.error("トレーニング保存失敗: セット内容が未入力");
				throw new IllegalArgumentException("セット内容を入力してください");
			}

			transaction.execute(training, principal);
			logger.info("トレーニングデータ保存完了 - ID: {}", training.getId());
		} catch (Exception e) {
			logger.error("トレーニングデータ保存中にエラー発生", e);
			throw e;
		}
	}

	public Training getTrainingById(Long id) {
		logger.debug("トレーニングデータ取得開始 - ID: {}", id);

		try {
			Training training = trainingDao.selectById(id);
			if (training != null) {
				logger.debug("トレーニングデータ取得成功 - ID: {}", id);
			} else {
				logger.warn("トレーニングデータが見つかりません - ID: {}", id);
			}
			return training;
		} catch (Exception e) {
			logger.error("トレーニングデータ取得中にエラー発生 - ID: {}", id, e);
			throw e;
		}
	}

	@Transactional
	public void deleteTraining(Long id) {
		logger.info("トレーニングデータ削除開始 - ID: {}", id);

		try {
			trainingDetailDao.deleteByTrainingId(id);
			logger.debug("トレーニング詳細データ削除完了 - ID: {}", id);

			Training training = trainingDao.selectById(id);
			if (training != null) {
				trainingDao.delete(training);
				logger.info("トレーニングデータ削除完了 - ID: {}", id);
			} else {
				logger.warn("削除対象のトレーニングデータが見つかりません - ID: {}", id);
			}
		} catch (Exception e) {
			logger.error("トレーニングデータ削除中にエラー発生 - ID: {}", id, e);
			throw e;
		}
	}

	public List<Training> getFullTrainingData(Long userId, LocalDate date) {
		logger.debug("フルトレーニングデータ取得開始 - ユーザーID: {}, 日付: {}", userId, date);

		try {
			List<Training> trainingList = trainingDao.selectByDate(userId, date, date);
			logger.debug("トレーニングリスト取得完了 - 件数: {}", trainingList.size());

			for (Training t : trainingList) {
				t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
				t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
			}

			logger.debug("フルトレーニングデータ取得完了 - ユーザーID: {}, 日付: {}", userId, date);
			return trainingList;
		} catch (Exception e) {
			logger.error("フルトレーニングデータ取得中にエラー発生 - ユーザーID: {}, 日付: {}", userId, date, e);
			throw e;
		}
	}

	@Transactional
	public void saveAll(List<Training> trainingList) {
		logger.info("一括トレーニングデータ保存開始 - 件数: {}", trainingList.size());

		try {
			for (Training training : trainingList) {
				Training currentDbData = trainingDao.selectById(training.getId());

				if (currentDbData != null) {
					currentDbData.setMemo(training.getMemo());
					currentDbData.setUpdatedDatetime(LocalDateTime.now());
					currentDbData.setDuration(training.getDuration());

					if (training.getDetails() != null && !training.getDetails().isEmpty()) {
						boolean allDone = training.getDetails().stream().allMatch(detail -> detail.getIsCompleted());
						currentDbData.setIsAllCompleted(allDone);
					} else {
						currentDbData.setIsAllCompleted(false);
					}

					trainingDao.update(currentDbData);
					logger.debug("トレーニングデータ更新完了 - ID: {}", training.getId());
				}

				// Delete existing details and insert new ones in batch for better performance
				trainingDetailDao.deleteByTrainingId(training.getId());
				if (training.getDetails() != null && !training.getDetails().isEmpty()) {
					// Prepare details for batch insert
					List<TrainingDetail> detailsToInsert = new ArrayList<>();
					for (TrainingDetail detail : training.getDetails()) {
						detail.setTrainingId(training.getId());
						detail.setIsCompleted(detail.getIsCompleted());
						detailsToInsert.add(detail);
					}
					// Batch insert for better performance
					for (TrainingDetail detail : detailsToInsert) {
						trainingDetailDao.insert(detail);
					}
					logger.debug("トレーニング詳細データ一括挿入完了 - トレーニングID: {}, 詳細件数: {}",
							training.getId(), detailsToInsert.size());
				}
			}

			logger.info("一括トレーニングデータ保存完了 - 件数: {}", trainingList.size());
		} catch (Exception e) {
			logger.error("一括トレーニングデータ保存中にエラー発生 - 件数: {}", trainingList.size(), e);
			throw e;
		}
	}

	public Long getUserIdByName(String username) {
		logger.debug("ユーザーID取得開始 - ユーザー名: {}", username);

		try {
			Long userId = trainingDao.selectIdByUsername(username);
			if (userId == null) {
				logger.warn("ユーザーが見つかりません - ユーザー名: {}, デフォルトID: 1 を使用", username);
				return 1L;
			}
			logger.debug("ユーザーID取得完了 - ユーザー名: {}, ID: {}", username, userId);
			return userId;
		} catch (Exception e) {
			logger.error("ユーザーID取得中にエラー発生 - ユーザー名: {}", username, e);
			throw e;
		}
	}

	public List<TrainingDetail> findByDate(String date) {
		logger.debug("日付別トレーニング詳細取得開始 - 日付: {}", date);

		try {
			List<TrainingDetail> details = trainingDetailDao.selectByDate(date);
			logger.debug("日付別トレーニング詳細取得完了 - 日付: {}, 件数: {}", date, details.size());
			return details;
		} catch (Exception e) {
			logger.error("日付別トレーニング詳細取得中にエラー発生 - 日付: {}", date, e);
			throw e;
		}
	}

	public List<TrainingDetail> findByUserIdAndDate(Long userId, String date) {
		logger.debug("ユーザーID・日付別トレーニング詳細取得開始 - ユーザーID: {}, 日付: {}", userId, date);

		try {
			List<TrainingDetail> details = trainingDetailDao.selectByUserIdAndDate(userId, date);
			logger.debug("ユーザーID・日付別トレーニング詳細取得完了 - ユーザーID: {}, 日付: {}, 件数: {}", userId, date, details.size());
			return details;
		} catch (Exception e) {
			logger.error("ユーザーID・日付別トレーニング詳細取得中にエラー発生 - ユーザーID: {}, 日付: {}", userId, date, e);
			throw e;
		}
	}

	public User getUserByName(String userName) {
		logger.debug("ユーザー情報取得開始 - ユーザー名: {}", userName);

		try {
			User user = trainingDao.selectByUserName(userName);
			if (user != null) {
				logger.debug("ユーザー情報取得完了 - ユーザー名: {}, ユーザーID: {}", userName, user.getUserId());
			} else {
				logger.warn("ユーザー情報が見つかりません - ユーザー名: {}", userName);
			}
			return user;
		} catch (Exception e) {
			logger.error("ユーザー情報取得中にエラー発生 - ユーザー名: {}", userName, e);
			throw e;
		}
	}

	public Map<String, Object> makeChartDataCustom(Long userId, String startStr, String endStr) {
		logger.info("チャートデータ生成開始 - ユーザーID: {}, 開始日: {}, 終了日: {}", userId, startStr, endStr);

		try {
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

			logger.info("チャートデータ生成完了 - ユーザーID: {}, データ件数: {}", userId, labels.size());
			return chartData;
		} catch (Exception e) {
			logger.error("チャートデータ生成中にエラー発生 - ユーザーID: {}, 開始日: {}, 終了日: {}", userId, startStr, endStr, e);
			throw e;
		}
	}

	private List<Double> getSafeVolumeData(Long userId, String partCode, List<String> labels, String startStr,
			String endStr) {
		logger.debug("部位別ボリュームデータ取得開始 - ユーザーID: {}, 部位: {}", userId, partCode);

		try {
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

			logger.debug("部位別ボリュームデータ取得完了 - ユーザーID: {}, 部位: {}, データ件数: {}", userId, partCode, result.size());
			return result;
		} catch (Exception e) {
			logger.error("部位別ボリュームデータ取得中にエラー発生 - ユーザーID: {}, 部位: {}", userId, partCode, e);
			throw e;
		}
	}
}
