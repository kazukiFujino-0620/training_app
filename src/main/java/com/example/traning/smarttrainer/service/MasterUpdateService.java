package com.example.traning.smarttrainer.service;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.organization.Organization;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
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
   * @throws IOException ファイル操作エラー時
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
        logger.warn(
            "Path traversal attack detected: attempted path='{}', canonical='{}'",
            filePath,
            canonicalPath);
        throw new SecurityException(
            "Path traversal detected: file must be under " + allowedDirectory);
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

  /** CSV読み込み結果。新規追加分と、master_flg変更のあった既存分を分けて保持する。 */
  private record ParsedCsv(
      List<TrainingItemMaster> newItems, List<TrainingItemMaster> updatedItems) {}

  @Transactional
  public int importCsv(File file, List<TrainingMaster> existingParts) throws Exception {
    // ファイルパスの検証（セキュリティ対策）
    try {
      file = validateAndNormalizeFilePath(file.getAbsolutePath());
    } catch (SecurityException | IOException e) {
      logger.error("File validation failed: {}", e.getMessage());
      throw e;
    }

    logger.info("CSVインポート開始 - ファイル: {}", file.getAbsolutePath());

    // 既存アイテムの保持用（新規判定・master_flg変更検知用）
    Map<String, TrainingItemMaster> existingItems = new HashMap<>();
    // 部位ごとの現在の最大連番管理用
    Map<String, Integer> orderMap = new HashMap<>();

    // データベースから既存のアイテムを取得してセットアップ
    logger.debug("既存マスタデータのセットアップ開始");
    for (TrainingMaster part : existingParts) {
      List<TrainingItemMaster> items = trainingMasterDao.selectItemsByPart(part.getPartCode());
      int maxOrder = 0;
      for (TrainingItemMaster item : items) {
        existingItems.put(item.getPartCode() + ":" + item.getItemName(), item);
        maxOrder = Math.max(maxOrder, item.getDisplayOrder() != null ? item.getDisplayOrder() : 0);
      }
      orderMap.put(part.getPartCode(), maxOrder);
    }
    logger.debug("既存マスタデータセットアップ完了 - 既存件数: {}", existingItems.size());

    ParsedCsv parsed = readCsvFile(file, existingItems, orderMap);
    logger.info(
        "CSVファイル読み込み完了 - 新規: {}件, master_flg更新: {}件",
        parsed.newItems().size(),
        parsed.updatedItems().size());

    int totalProcessed = 0;

    // 新規種目の登録（既存行を含まないため、ユニーク制約違反は発生しない）
    if (!parsed.newItems().isEmpty()) {
      for (List<TrainingItemMaster> batch : createBatches(parsed.newItems(), 100)) {
        trainingMasterDao.batchUpsert(batch);
        totalProcessed += batch.size();
      }
      logger.info("{} 件のマスタデータを新規登録しました。", parsed.newItems().size());
    }

    // 既存種目のmaster_flg更新
    if (!parsed.updatedItems().isEmpty()) {
      for (List<TrainingItemMaster> batch : createBatches(parsed.updatedItems(), 100)) {
        trainingMasterDao.batchUpdateMasterFlg(batch);
        totalProcessed += batch.size();
      }
      logger.info("{} 件のマスタデータのmaster_flgを更新しました。", parsed.updatedItems().size());
    }

    if (totalProcessed == 0) {
      logger.warn("取り込むデータがありませんでした。");
    }

    logger.info("CSVインポート完了");
    return totalProcessed;
  }

  private ParsedCsv readCsvFile(
      File file, Map<String, TrainingItemMaster> existingItems, Map<String, Integer> orderMap)
      throws Exception {
    List<TrainingItemMaster> newItems = new ArrayList<>();
    List<TrainingItemMaster> updatedItems = new ArrayList<>();
    // 同一ファイル内の重複行対策（新規追加分）
    Set<String> seenInFile = new HashSet<>();

    // 1. ファイルを1行ずつ読み込む
    try (java.io.BufferedReader br =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {

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

        // 列数チェック（parts_code と item_name は最低限必要。master_flg列は無くても良い）
        if (data.length < 2) {
          logger.warn("不適切な行をスキップしました: {}", line);
          continue;
        }

        processRow(data, existingItems, orderMap, seenInFile, newItems, updatedItems);
      }
    }

    return new ParsedCsv(newItems, updatedItems);
  }

  private void processRow(
      String[] data,
      Map<String, TrainingItemMaster> existingItems,
      Map<String, Integer> orderMap,
      Set<String> seenInFile,
      List<TrainingItemMaster> newItems,
      List<TrainingItemMaster> updatedItems) {
    String partsCode = data[0].trim();
    String itemName = data[1].trim();

    if (partsCode.isEmpty() || itemName.isEmpty()) {
      logger.warn("空のパーツコードまたはアイテム名をスキップしました");
      return;
    }

    int masterFlg = parseMasterFlg(data);
    String key = partsCode + ":" + itemName;

    TrainingItemMaster existing = existingItems.get(key);
    if (existing != null) {
      // --- 既存種目: master_flgに差分があれば更新対象に含める。無ければ何もしない ---
      Integer currentFlg = existing.getMasterFlg();
      if (currentFlg != null && currentFlg == masterFlg) {
        return;
      }
      TrainingItemMaster entity = new TrainingItemMaster();
      entity.setId(existing.getId());
      entity.setPartCode(partsCode);
      entity.setItemName(itemName);
      entity.setDisplayOrder(existing.getDisplayOrder());
      entity.setMasterFlg(masterFlg);
      // organization_id・range_of_motion_mはDB上NOT NULL。BatchUpdateは全列を明示的に列挙する
      // 自動生成SQLのため、既存値を引き継がないとNULLで上書きされてしまう。
      entity.setOrganizationId(existing.getOrganizationId());
      entity.setRangeOfMotionM(existing.getRangeOfMotionM());
      updatedItems.add(entity);
      return;
    }

    // --- 同一ファイル内の重複対策（新規追加分） ---
    if (!seenInFile.add(key)) {
      return;
    }

    TrainingItemMaster entity = new TrainingItemMaster();
    entity.setPartCode(partsCode);
    entity.setItemName(itemName);
    entity.setMasterFlg(masterFlg);
    // CSV一括登録の種目はプラットフォーム共通種目として扱う（組織固有種目の登録UIはita1-1未実施分で対応）。
    entity.setOrganizationId(Organization.ALL_ORGANIZATION_ID);
    // range_of_motion_mはDB上NOT NULL（DEFAULT 0.40）。BatchInsertは全列を明示的に列挙する自動生成SQLのため、
    // ここで未設定のままだとNULLが明示的にバインドされDBのDEFAULT句が効かず登録に失敗する（ita2結合試験で発見）。
    entity.setRangeOfMotionM(new BigDecimal("0.40"));

    // --- 部位ごとの連番ロジック ---
    // その部位が初めて登場なら1、次からは+1する
    int nextOrder = orderMap.getOrDefault(partsCode, 0) + 1;
    entity.setDisplayOrder(nextOrder);
    orderMap.put(partsCode, nextOrder); // 最新の番号を保存
    // ----------------------------

    newItems.add(entity);
  }

  /** CSVの3列目（master_flg）を解析する。列が無い・空・不正な値の場合は1（使用可能）として扱う。 */
  private int parseMasterFlg(String[] data) {
    if (data.length < 3 || data[2].trim().isEmpty()) {
      return 1;
    }
    try {
      int value = Integer.parseInt(data[2].trim());
      if (value != 0 && value != 1) {
        logger.warn("不正なmaster_flg値のため1として扱います: {}", value);
        return 1;
      }
      return value;
    } catch (NumberFormatException e) {
      logger.warn("master_flgの解析に失敗したため1として扱います: {}", data[2]);
      return 1;
    }
  }

  private List<List<TrainingItemMaster>> createBatches(
      List<TrainingItemMaster> list, int batchSize) {
    List<List<TrainingItemMaster>> batches = new ArrayList<>();
    for (int i = 0; i < list.size(); i += batchSize) {
      batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
    }
    return batches;
  }
}
