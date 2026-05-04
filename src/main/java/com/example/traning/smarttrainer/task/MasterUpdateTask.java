package com.example.traning.smarttrainer.task;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.smarttrainer.service.MasterUpdateService;

@Component
public class MasterUpdateTask {

    private static final Logger logger = LoggerFactory.getLogger(MasterUpdateTask.class);

    @Autowired
    private MasterUpdateService masterUpdateService;

    @Autowired
    private TrainingMasterDao trainingMasterDao;

    @Value("${batch.master.update.file-path}")
    private String filePath;

    @Scheduled(cron = "${batch.master.update.cron}")
    public void executeMasterUpdate() {
        logger.info("--- 夜間マスタ更新バッチ 開始 ---");

        File file = new File(filePath);

        if (!file.exists()) {
            logger.warn("更新用CSVファイルが見つかりません。パス: {}", filePath);
            return;
        }

        logger.info("マスタ情報取得します");
        List<TrainingMaster> trainingMasterList = trainingMasterDao.selectAll();

        if (trainingMasterList.isEmpty()) {
            logger.warn("マスタ情報が見つかりません。");
        }
        try {
            logger.info("ファイルを正常に検知しました。処理を開始します。");

            masterUpdateService.importCsv(file, trainingMasterList);
            // --------------------

        } catch (Exception e) {
            logger.error("バッチ処理中にエラーが発生しました", e);
        }

        logger.info("--- 夜間マスタ更新バッチ 終了 ---");
    }

    // 【テスト用】起動後5秒ごとに実行（動作確認用。確認が終わったら消すかコメントアウト）
    // @Scheduled(fixedRate = 5000)
    // public void testRun() {
    // logger.info("バッチ起動テスト中...");
    // }
}
