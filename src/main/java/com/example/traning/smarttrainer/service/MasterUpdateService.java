package com.example.traning.smarttrainer.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;

@Service
public class MasterUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(MasterUpdateService.class);

    @Autowired
    private TrainingMasterDao trainingMasterDao;

    @Transactional
    public void importCsv(File file, List<TrainingMaster> existingParts) throws Exception {
        // 既存データのキー保持用（重複チェック用）
        Set<String> existingKeys = new HashSet<>();
        // 部位ごとの現在の最大連番管理用
        Map<String, Integer> orderMap = new HashMap<>();

        // データベースから既存のアイテムを取得してセットアップ
        for (TrainingMaster part : existingParts) {
            List<TrainingItemMaster> items = trainingMasterDao.selectItemsByPart(part.getPartCode());
            int maxOrder = 0;
            for (TrainingItemMaster item : items) {
                existingKeys.add(item.getPartCode() + ":" + item.getItemName());
                maxOrder = Math.max(maxOrder, item.getDisplayOrder() != null ? item.getDisplayOrder() : 0);
            }
            orderMap.put(part.getPartCode(), maxOrder);
        }

        List<TrainingItemMaster> itemList = new ArrayList<>();

        // 1. ファイルを1行ずつ読み込む
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"))) {

            String line;
            boolean isFirstLine = true;

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

                // --- 重複チェック ---
                String key = partsCode + ":" + itemName;
                if (existingKeys.contains(key)) {
                    // すでにDB（または今回のリスト）に存在する場合はスキップ
                    continue;
                }

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
                existingKeys.add(key); // 同一ファイル内の重複対策
            }
        }

        // 5. リストが空でなければDBへ保存（UPSERT）
        if (!itemList.isEmpty()) {
            trainingMasterDao.batchUpsert(itemList);
            logger.info("{} 件のマスタデータを更新・登録しました。", itemList.size());
        } else {
            logger.warn("取り込むデータがありませんでした。");
        }
    }
}