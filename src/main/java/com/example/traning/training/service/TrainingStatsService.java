package com.example.traning.training.service;

import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.weekly.WeeklyProgram;
import com.example.traning.weekly.WeeklyProgramService;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ホーム画面（Web版 /menu）の統計バー（今月の回数・先週比ボリューム・今週の部位カバレッジ・今日の曜日別プログラム）を算出する。
 *
 * <p>元々は {@code MenuController.menu()} に直書きされていたロジック（ita7-2でモバイルにも同じ統計を表示するために切り出し）。 Web側（{@code
 * MenuController}）・モバイル側（{@code MobileStatsController}）の両方から呼び出す共通ロジックとして提供する。
 * Web用テンプレート（menu.html）が既存の {@code Map<String,Object>}（キー: name, done）で {@code weekParts}
 * にアクセスしているため、切り出し後もWeb側の出力が完全に同一になるよう {@link #weekPartsAsMapList()} を用意している。
 */
@Service
@RequiredArgsConstructor
public class TrainingStatsService {

  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final WeeklyProgramService weeklyProgramService;

  private static final Map<String, String> PART_LABEL_MAP =
      Map.of("CHEST", "胸", "BACK", "背中", "SHOULDER", "肩", "ARM", "腕", "LEG", "脚");

  private static final String[][] PART_DEFS = {
    {"CHEST", "胸"}, {"BACK", "背中"}, {"SHOULDER", "肩"}, {"ARM", "腕"}, {"LEG", "脚"}
  };

  @Transactional(readOnly = true)
  public TrainingStats getStats(Long userId, LocalDate today) {
    // R1: 今月のトレーニング回数
    int monthlyCount =
        trainingDao.countByUserIdAndMonth(userId, today.getYear(), today.getMonthValue());

    // R2: 今週（月曜起点）の部位カバレッジ
    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
    Set<String> weekPartsDoneSet =
        new HashSet<>(
            trainingDao.selectDistinctPartsByUserIdAndDateRange(userId, weekStart, today));
    List<PartCoverage> weekParts = new ArrayList<>();
    for (String[] pd : PART_DEFS) {
      weekParts.add(new PartCoverage(pd[0], pd[1], weekPartsDoneSet.contains(pd[0])));
    }

    // R3: 前週比ボリューム
    LocalDate prevWeekStart = weekStart.minusWeeks(1);
    LocalDate prevWeekEnd = weekStart.minusDays(1);
    Double thisWeekVolume =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, weekStart, today);
    Double prevWeekVolume =
        trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, prevWeekStart, prevWeekEnd);
    String volumeChangeText;
    boolean volumeChangePositive = true;
    Integer volumeChangePct = null;
    if (prevWeekVolume == null || prevWeekVolume == 0.0) {
      volumeChangeText = "前週データなし";
    } else {
      double thisVol = thisWeekVolume != null ? thisWeekVolume : 0.0;
      int pctChange = (int) Math.round((thisVol - prevWeekVolume) / prevWeekVolume * 100);
      volumeChangePositive = pctChange >= 0;
      volumeChangeText = pctChange >= 0 ? "+" + pctChange + "%" : pctChange + "%";
      volumeChangePct = pctChange;
    }

    // 週間プログラム: 今日の予定
    WeeklyProgram todayProgram = weeklyProgramService.getTodayProgram(userId).orElse(null);
    String todayPartCode = todayProgram != null ? todayProgram.getPartCode() : null;
    String todayPartLabel =
        todayPartCode != null ? PART_LABEL_MAP.getOrDefault(todayPartCode, "") : null;

    return new TrainingStats(
        monthlyCount,
        weekParts,
        volumeChangeText,
        volumeChangePositive,
        volumeChangePct,
        todayProgram,
        todayPartCode,
        todayPartLabel);
  }

  /** 今週、指定した部位のトレーニングを実施済みかどうか（部位コード＋日本語名＋実施済みフラグ）。 */
  public record PartCoverage(String partCode, String name, boolean done) {}

  public record TrainingStats(
      int monthlyCount,
      List<PartCoverage> weekParts,
      String volumeChangeText,
      boolean volumeChangePositive,
      Integer volumeChangePct,
      WeeklyProgram todayProgram,
      String todayPartCode,
      String todayPartLabel) {

    /**
     * Web用Thymeleafテンプレート（menu.html）が {@code ${part.name}} / {@code ${part.done}}
     * というMapキーアクセスで参照している既存仕様に合わせた変換。切り出し前と完全に同一の構造を返す。
     */
    public List<Map<String, Object>> weekPartsAsMapList() {
      List<Map<String, Object>> result = new ArrayList<>();
      for (PartCoverage pc : weekParts) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", pc.name());
        row.put("done", pc.done());
        result.add(row);
      }
      return result;
    }
  }
}
