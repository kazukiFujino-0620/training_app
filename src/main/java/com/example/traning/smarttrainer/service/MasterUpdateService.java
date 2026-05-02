package com.example.traning.smarttrainer.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.training.TrainingDao;
import com.example.traning.entity.TrainingItemMaster;

@Service
public class MasterUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(MasterUpdateService.class);

    @Autowired
    private TrainingDao trainingDao;

    @Transactional
    public void importCsv(File file) throws Exception {
        List<TrainingItemMaster> itemList = new ArrayList<>();

        // 1. ファイルを1行ずつ読み込む
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {

            String line;
            boolean isFirstLine = true;

            Map<String, Integer> orderMap = new HashMap<>(); // 部位ごとの連番管理用

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // 空行対策
                if (line.trim().isEmpty())
                    continue;

                String[] data = line.split(",", -1);

                // 列数チェック（parts_code と item_name は最低限必要）
                if (data.length < 2) {
                    logger.warn("不適切な行をスキップしました: {}", line);
                    continue;
                }

                String partsCode = data[0];
                String itemName = data[1];

                TrainingItemMaster entity = new TrainingItemMaster();
                entity.setPartCode(partsCode);
                entity.setItemName(itemName);

                // --- 部位ごとの連番ロジック ---
                // その部位が初めて登場なら1、次からは+1する
                int nextOrder = orderMap.getOrDefault(partsCode, 0) + 1;
                entity.setDisplayOrder(nextOrder);
                orderMap.put(partsCode, nextOrder); // 最新の番号を保存
                // ----------------------------

                itemList.add(entity);
            }
        }

        // 5. リストが空でなければDBへ保存（UPSERT）
        if (!itemList.isEmpty()) {
            trainingDao.batchUpsert(itemList);
            logger.info("{} 件のマスタデータを更新・登録しました。", itemList.size());
        } else {
            logger.warn("取り込むデータがありませんでした。");
        }
    }
}