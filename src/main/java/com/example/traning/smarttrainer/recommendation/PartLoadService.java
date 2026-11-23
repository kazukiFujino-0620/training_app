package com.example.traning.smarttrainer.recommendation;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingMaster;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 部位別の直近負荷（人体図と部位別の表）の表示用データ。計算は {@link FatigueCalculator}（過去7日間・48時間半減期）に委譲する。
 *
 * <p>会員本人のトップ（MenuController）と、スタッフ向けの会員詳細（機能見直し-1-#19）で同じ計算・同じ行の形を使うために切り出した。
 */
@Service
public class PartLoadService {

  private final FatigueCalculator fatigueCalculator;
  private final TrainingMasterDao trainingMasterDao;

  public PartLoadService(FatigueCalculator fatigueCalculator, TrainingMasterDao trainingMasterDao) {
    this.fatigueCalculator = fatigueCalculator;
    this.trainingMasterDao = trainingMasterDao;
  }

  /** 指定ユーザーの部位別の行（{@link FatigueCalculator#PART_ORDER} の順）。 */
  public List<Map<String, Object>> loadRows(Long userId, LocalDate today) {
    return toRows(fatigueCalculator.calculate(userId, today), trainingMasterDao.selectAllParts());
  }

  /**
   * 計算結果を表示用の行にする。Thymeleaf のMap変数キーアクセス問題を避けるため、List<Map>で返す。
   *
   * <p>各行: partCode・partName・volume（kg）・sets・pct（人体図の色分けに使う0〜100）。
   */
  public static List<Map<String, Object>> toRows(
      FatigueCalculator.FatigueResult result, List<TrainingMaster> partList) {
    Map<String, String> partNameMap =
        partList.stream()
            .collect(
                Collectors.toMap(
                    TrainingMaster::getPartCode, TrainingMaster::getPartName, (a, b) -> a));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (String p : FatigueCalculator.PART_ORDER) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("partCode", p);
      row.put("partName", partNameMap.getOrDefault(p, p));
      row.put("volume", result.volumeByPart().getOrDefault(p, 0L));
      row.put("sets", result.setsByPart().getOrDefault(p, 0));
      row.put("pct", result.fatiguePct().getOrDefault(p, 0));
      rows.add(row);
    }
    return rows;
  }
}
