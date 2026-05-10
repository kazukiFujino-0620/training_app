package com.example.traning.smarttrainer.task;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.smarttrainer.service.MasterUpdateService;

@Component
public class MasterUpdateTask {

    private static final Logger logger = LoggerFactory.getLogger(MasterUpdateTask.class);

    private final MasterUpdateService masterUpdateService;
    private final TrainingMasterDao trainingMasterDao;

    public MasterUpdateTask(MasterUpdateService masterUpdateService, TrainingMasterDao trainingMasterDao) {
        this.masterUpdateService = masterUpdateService;
        this.trainingMasterDao = trainingMasterDao;
        logger.info("MasterUpdateTask 初期化完了");
    }

    @Value("${batch.master.update.file-path}")
    private String filePath;

    @Scheduled(cron = "${batch.master.update.cron}")
    public void executeMasterUpdate() {
        logger.info("=== 夜間マスタ更新バッチ 開始 ===");

        try {
            File file = new File(filePath);
            logger.debug("CSVファイルパス確認: {}", filePath);

            if (!file.exists()) {
                logger.warn("更新用CSVファイルが見つかりません。パス: {}", filePath);
                return;
            }

            logger.info("CSVファイルを確認: 存在します - サイズ: {} bytes", file.length());

            logger.info("マスタ情報取得します");
            List<TrainingMaster> trainingMasterList = trainingMasterDao.selectAll();
            logger.info("既存マスタ情報取得完了 - 件数: {}", trainingMasterList.size());

            if (trainingMasterList.isEmpty()) {
                logger.warn("マスタ情報が見つかりません。");
            }

            logger.info("ファイルを正常に検知しました。処理を開始します。");
            masterUpdateService.importCsv(file, trainingMasterList);

            logger.info("=== 夜間マスタ更新バッチ 正常終了 ===");

        } catch (Exception e) {
            logger.error("バッチ処理中にエラーが発生しました", e);
            logger.error("=== 夜間マスタ更新バッチ 異常終了 ===");
        }
    }

    // 【テスト用】起動後5秒ごとに実行（動作確認用。確認が終わったら消すかコメントアウト）
    // @Scheduled(fixedRate = 5000)
    // public void testRun() {
    // logger.info("バッチ起動テスト中...");
    // }
}
