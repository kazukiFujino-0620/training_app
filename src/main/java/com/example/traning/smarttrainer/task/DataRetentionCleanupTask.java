package com.example.traning.smarttrainer.task;

import com.example.traning.dao.UserDao;
import com.example.traning.retention.DataRetentionService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class DataRetentionCleanupTask {

  private final TrainingDetailDao trainingDetailDao;
  private final TrainingDao trainingDao;
  private final UserDao userDao;
  private final DataRetentionService dataRetentionService;

  public DataRetentionCleanupTask(
      TrainingDetailDao trainingDetailDao,
      TrainingDao trainingDao,
      UserDao userDao,
      DataRetentionService dataRetentionService) {
    this.trainingDetailDao = trainingDetailDao;
    this.trainingDao = trainingDao;
    this.userDao = userDao;
    this.dataRetentionService = dataRetentionService;
  }

  /** 深夜0時に、論理削除済みかつ保護期間（7年）を超えたレコードを物理削除する。 */
  @Scheduled(cron = "${batch.master.retentionCleanup.cron}")
  @Transactional
  public void cleanupExpiredData() {
    LocalDateTime cutoff = dataRetentionService.getRetentionCutoff();
    log.info("データ保護期間超過レコードの物理削除開始 - 基準日時: {}", cutoff);

    // 外部キー制約があるため training_details を先に削除する
    int detailCount = trainingDetailDao.deleteExpiredPhysically(cutoff);
    int trainingCount = trainingDao.deleteExpiredPhysically(cutoff);
    int userCount = userDao.deleteExpiredPhysically(cutoff);

    log.info("物理削除完了 - 詳細: {} 件, トレーニング: {} 件, ユーザー: {} 件", detailCount, trainingCount, userCount);
  }
}
