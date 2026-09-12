package com.example.traning.smarttrainer.recommendation;

import com.example.traning.training.SetType;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 部位別疲労度の計算（過去7日間・48時間半減期モデル）。 元々 MenuController にインラインで実装されていたロジックを抽出し、 RecommendationService
 * からも共通で使えるようにしたもの。
 */
@Service
public class FatigueCalculator {

  public static final String[] PART_ORDER = {"CHEST", "BACK", "SHOULDER", "ARM", "LEG"};

  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;

  public FatigueCalculator(TrainingDao trainingDao, TrainingDetailDao trainingDetailDao) {
    this.trainingDao = trainingDao;
    this.trainingDetailDao = trainingDetailDao;
  }

  public FatigueResult calculate(Long userId, LocalDate today) {
    LocalDate fatigueStart = today.minusDays(6);
    List<Training> fatigueTrainings =
        trainingDao.selectByUserIdAndDateRange(userId.intValue(), fatigueStart, today);

    Map<String, Long> volumeByPart = new LinkedHashMap<>();
    Map<String, Integer> setsByPart = new LinkedHashMap<>();
    Map<String, Double> rawFatigueByPart = new LinkedHashMap<>();
    for (String p : PART_ORDER) {
      volumeByPart.put(p, 0L);
      setsByPart.put(p, 0);
      rawFatigueByPart.put(p, 0.0);
    }

    for (Training ft : fatigueTrainings) {
      String pc = ft.getPartCode();
      if (pc == null || !volumeByPart.containsKey(pc)) continue;
      List<TrainingDetail> fDetails = trainingDetailDao.selectByTrainingId(ft.getId());
      long daysAgo = ChronoUnit.DAYS.between(ft.getTrainingDate(), today);
      double decay = Math.pow(0.5, daysAgo / 2.0); // 48時間で疲労50%回復
      long vol = 0;
      int completedSets = 0;
      for (TrainingDetail fd : fDetails) {
        if (!fd.getIsCompleted()) continue;
        boolean isWarmup = SetType.fromValueOrMain(fd.getSetType()) == SetType.WARMUP;
        if (!isWarmup && fd.getWeight() != null && fd.getReps() != null) {
          vol += Math.round(fd.getWeight() * fd.getReps());
        }
        completedSets++;
      }
      volumeByPart.merge(pc, vol, Long::sum);
      setsByPart.merge(pc, completedSets, Integer::sum);
      rawFatigueByPart.merge(pc, vol * decay, Double::sum);
    }

    Map<String, Integer> fatiguePct = new LinkedHashMap<>();
    for (String p : PART_ORDER) {
      long rawVol = volumeByPart.get(p);
      double decayed = rawFatigueByPart.get(p);
      int pct = rawVol > 0 ? (int) Math.round(decayed / rawVol * 100) : 0;
      fatiguePct.put(p, pct);
    }

    return new FatigueResult(fatiguePct, volumeByPart, setsByPart);
  }

  public record FatigueResult(
      Map<String, Integer> fatiguePct,
      Map<String, Long> volumeByPart,
      Map<String, Integer> setsByPart) {}
}
