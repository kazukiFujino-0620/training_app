package com.example.traning.smarttrainer.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;

@Service
public class MasterUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(MasterUpdateService.class);

    private final TrainingMasterDao trainingMasterDao;

    @Value("${batch.master.update.allowed-directory:/var/data/training-app/imports/}")
    private String allowedDirectory;

    public MasterUpdateService(TrainingMasterDao trainingMasterDao) {
        this.trainingMasterDao = trainingMasterDao;
    }

    /**
     * パストラバーサル攻撃を防ぐためにファイルパスを検証
     * 
     * @param filePath 検証対象のファイルパス
     * @return 検証済みのファイルオブジェクト
     * @throws SecurityException パストラバーサル検出時
     * @throws IOException       ファイル操作エラー時
     */
    public File validateAndNormalizeFilePath(String filePath) throws SecurityException, IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new SecurityException("File path cannot be null or empty");
        }

        try {
            // ファイルパスを正規化（相対パスを解決）
            File file = new File(filePath);
            Path canonicalPath = file.getCanonicalFile().toPath();

            // 許可されたディレクトリを正規化（toRealPath の代わりに getCanonicalFile を使用）
            File allowedDir = new File(allowedDirectory);
            if (!allowedDir.exists() || !allowedDir.isDirectory()) {
                logger.error("Allowed directory does not exist: {}", allowedDirectory);
                throw new SecurityException("Configuration error: allowed directory not available");
            }
            Path allowedPath = allowedDir.getCanonicalFile().toPath();

            // パストラバーサルチェック：許可ディレクトリ内にあるか確認
            if (!canonicalPath.startsWith(allowedPath)) {
                logger.warn("Path traversal attack detected: attempted path='{}', canonical='{}'",
                        filePath, canonicalPath);
                throw new SecurityException("Path traversal detected: file must be under " + allowedDirectory);
            }

            // ファイルが実際に存在するか、かつファイルであるか確認
            if (!file.exists()) {
                logger.warn("File does not exist: {}", filePath);
                throw new IOException("File not found: " + filePath);
            }

            if (!file.isFile()) {
                logger.warn("Path is not a file: {}", filePath);
                throw new IOException("Path is not a file: " + filePath);
            }

            // 読み取り可能性確認
            if (!file.canRead()) {
                logger.warn("File is not readable: {}", filePath);
                throw new IOException("File is not readable: " + filePath);
            }

            logger.info("File path validation passed: {}", file.getAbsolutePath());
            return file;

        } catch (SecurityException | IOException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during path validation", e);
            throw new SecurityException("Path validation error: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void importCsv(File file, List<TrainingMaster> existingParts) throws Exception {
        // ファイルパスの検証（セキュリティ対策）
        try {
            file = validateAndNormalizeFilePath(file.getAbsolutePath());
        } catch (SecurityException | IOException e) {
            logger.error("File validation failed: {}", e.getMessage());
            throw e;
        }

        logger.info("CSVインポート開始 - ファイル: {}", file.getAbsolutePath());

        // 既存データのキー保持用（重複チェック用）
        Set<String> existingKeys = new HashSet<>();
        // 部位ごとの現在の最大連番管理用
        Map<String, Integer> orderMap = new HashMap<>();

        // データベースから既存のアイテムを取得してセットアップ
        logger.debug("既存マスタデータのセットアップ開始");
        for (TrainingMaster part : existingParts) {
            List<TrainingItemMaster> items = trainingMasterDao.selectItemsByPart(part.getPartCode());
            int maxOrder = 0;
            for (TrainingItemMaster item : items) {
                existingKeys.add(item.getPartCode() + ":" + item.getItemName());
                maxOrder = Math.max(maxOrder, item.getDisplayOrder() != null ? item.getDisplayOrder() : 0);
            }
            orderMap.put(part.getPartCode(), maxOrder);
        }
        logger.debug("既存マスタデータセットアップ完了 - 既存キー数: {}", existingKeys.size());

        List<TrainingItemMaster> itemList = readCsvFile(file, existingKeys, orderMap);
        logger.info("CSVファイル読み込み完了 - 新規データ件数: {}", itemList.size());

        // 5. リストが空でなければDBへ保存（UPSERT）
        if (!itemList.isEmpty()) {
            // バッチ処理でパフォーマンス向上
            List<List<TrainingItemMaster>> batches = createBatches(itemList, 100);
            logger.info("バッチ処理開始 - バッチ数: {}", batches.size());

            int totalProcessed = 0;
            for (int i = 0; i < batches.size(); i++) {
                List<TrainingItemMaster> batch = batches.get(i);
                trainingMasterDao.batchUpsert(batch);
                totalProcessed += batch.size();
                logger.debug("バッチ処理完了 - バッチ {}/{}, 処理件数: {}", i + 1, batches.size(), batch.size());
            }

            logger.info("{} 件のマスタデータを更新・登録しました。", totalProcessed);
        } else {
            logger.warn("取り込むデータがありませんでした。");
        }

        logger.info("CSVインポート完了");
    }

    private List<TrainingItemMaster> readCsvFile(File file, Set<String> existingKeys, Map<String, Integer> orderMap)
            throws Exception {
        List<TrainingItemMaster> itemList = new ArrayList<>();

        // 1. ファイルを1行ずつ読み込む
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(file),
                        java.nio.charset.StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                // 空行対策
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] data = line.split(",", -1);

                // 列数チェック（parts_code と item_name は最低限必要）
                if (data.length < 2) {
                    logger.warn("不適切な行をスキップしました: {}", line);
                    continue;
                }

                TrainingItemMaster entity = createTrainingItemMaster(data, existingKeys, orderMap);
                if (entity != null) {
                    itemList.add(entity);
                }
            }
        }

        return itemList;
    }

    private TrainingItemMaster createTrainingItemMaster(String[] data, Set<String> existingKeys,
            Map<String, Integer> orderMap) {
        String partsCode = data[0];
        String itemName = data[1];

        // --- 重複チェック ---
        String key = partsCode + ":" + itemName;
        if (existingKeys.contains(key)) {
            // すでにDB（または今回のリスト）に存在する場合はスキップ
            return null;
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

        existingKeys.add(key); // 同一ファイル内の重複対策
        return entity;
    }

    private List<List<TrainingItemMaster>> createBatches(List<TrainingItemMaster> list, int batchSize) {
        List<List<TrainingItemMaster>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}