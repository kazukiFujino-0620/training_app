package com.example.traning.smarttrainer.task;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.smarttrainer.service.MasterUpdateService;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MasterUpdateTask {

  private static final Logger logger = LoggerFactory.getLogger(MasterUpdateTask.class);

  private final MasterUpdateService masterUpdateService;
  private final TrainingMasterDao trainingMasterDao;

  public MasterUpdateTask(
      MasterUpdateService masterUpdateService, TrainingMasterDao trainingMasterDao) {
    this.masterUpdateService = masterUpdateService;
    this.trainingMasterDao = trainingMasterDao;
    logger.info("MasterUpdateTask 初期化完了");
  }

  @Value("${batch.master.update.file-path}")
  private String filePath;

  /** バッチ実行結果。管理画面からの手動起動時に結果をユーザーへ表示するために使用する。 */
  public record MasterUpdateResult(boolean success, int processedCount, String message) {}

  @Scheduled(cron = "${batch.master.update.cron}")
  public void scheduledMasterUpdate() {
    executeMasterUpdate();
  }

  /**
   * マスタ更新処理の本体。夜間バッチ（cron）と管理画面からの手動起動の両方から呼び出される共通ロジック。
   *
   * @return 実行結果（成功可否・処理件数・メッセージ）
   */
  public MasterUpdateResult executeMasterUpdate() {
    logger.info("=== マスタ更新バッチ 開始 ===");

    try {
      // ファイルパスの検証を試行
      File file;
      try {
        // MasterUpdateService のバリデーションメソッドを使用
        file = masterUpdateService.validateAndNormalizeFilePath(filePath);
      } catch (Exception e) {
        logger.error("ファイルパス検証失敗: {}", e.getMessage());
        logger.error("=== マスタ更新バッチ 異常終了 ===");
        return new MasterUpdateResult(false, 0, "ファイルパスの検証に失敗しました: " + e.getMessage());
      }

      logger.debug("CSVファイルパス確認: {}", file.getAbsolutePath());

      if (!file.exists()) {
        logger.warn("更新用CSVファイルが見つかりません。パス: {}", file.getAbsolutePath());
        return new MasterUpdateResult(
            false, 0, "更新用CSVファイルが見つかりません。先にCSVファイルをアップロードしてください。");
      }

      logger.info("CSVファイルを確認: 存在します - サイズ: {} bytes", file.length());

      logger.info("マスタ情報取得します");
      List<TrainingMaster> trainingMasterList = trainingMasterDao.selectAllParts();
      logger.info("既存マスタ情報取得完了 - 件数: {}", trainingMasterList.size());

      if (trainingMasterList.isEmpty()) {
        logger.warn("マスタ情報が見つかりません。");
      }

      logger.info("ファイルを正常に検知しました。処理を開始します。");
      int processedCount = masterUpdateService.importCsv(file, trainingMasterList);

      logger.info("=== マスタ更新バッチ 正常終了 ===");
      return new MasterUpdateResult(
          true, processedCount, processedCount + " 件のマスタデータを取り込みました。");

    } catch (Exception e) {
      logger.error("バッチ処理中にエラーが発生しました", e);
      logger.error("=== マスタ更新バッチ 異常終了 ===");
      return new MasterUpdateResult(false, 0, "処理中にエラーが発生しました: " + e.getMessage());
    }
  }

  // 【テスト用】起動後5秒ごとに実行（動作確認用。確認が終わったら消すかコメントアウト）
  // @Scheduled(fixedRate = 5000)
  // public void testRun() {
  // logger.info("バッチ起動テスト中...");
  // }
}
