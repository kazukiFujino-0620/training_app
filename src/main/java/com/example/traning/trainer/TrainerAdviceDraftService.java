package com.example.traning.trainer;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.smarttrainer.coaching.AdviceCoach;
import com.example.traning.smarttrainer.coaching.AdviceContext;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.FatigueCalculator;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * ita5-1 機能2: トレーナーアドバイスのAI下書き。「AIで下書き」ボタン押下時のみ、都度その場で生成する
 * （日次キャッシュは行わない。機能1・機能3とは異なり、送信するたびに毎回異なる文面が求められるため）。
 */
@Service
public class TrainerAdviceDraftService {

  private final TrainerAdviceService trainerAdviceService;
  private final RecommendationService recommendationService;
  private final FatigueCalculator fatigueCalculator;
  private final PersonalRecordService personalRecordService;
  private final TrainingDao trainingDao;
  private final AdviceCoach adviceCoach;

  public TrainerAdviceDraftService(
      TrainerAdviceService trainerAdviceService,
      RecommendationService recommendationService,
      FatigueCalculator fatigueCalculator,
      PersonalRecordService personalRecordService,
      TrainingDao trainingDao,
      AdviceCoach adviceCoach) {
    this.trainerAdviceService = trainerAdviceService;
    this.recommendationService = recommendationService;
    this.fatigueCalculator = fatigueCalculator;
    this.personalRecordService = personalRecordService;
    this.trainingDao = trainingDao;
    this.adviceCoach = adviceCoach;
  }

  /**
   * 下書きを生成する。トレーナーがAI機能に同意していない場合は{@code Optional.empty()}を返す （呼び出し元で「設定画面で同意が必要」という案内表示に使う）。
   *
   * @throws IllegalArgumentException 宛先が操作者のスコープ外の場合
   */
  public Optional<String> generateDraft(User trainer, Long targetUserId, LocalDate targetDate) {
    if (!Boolean.TRUE.equals(trainer.getAiAdviceConsent())) {
      return Optional.empty();
    }

    User trainee =
        trainerAdviceService.listTrainees(trainer).stream()
            .filter(u -> u.getUserId().longValue() == targetUserId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("この宛先の下書きは生成できません"));

    Long traineeId = trainee.getUserId().longValue();
    List<Training> targetDateTrainings =
        trainingDao.selectByDate(traineeId, targetDate, targetDate);
    DailyRecommendation recommendation = recommendationService.getTodayRecommendation(traineeId);
    FatigueCalculator.FatigueResult fatigueResult =
        fatigueCalculator.calculate(traineeId, LocalDate.now());
    List<PersonalRecord> personalRecords = personalRecordService.getByUserId(traineeId);

    AdviceContext context =
        new AdviceContext(
            trainee.getUserName(),
            targetDate,
            targetDateTrainings,
            recommendation,
            fatigueResult,
            personalRecords);

    return Optional.of(adviceCoach.generateDraft(context));
  }
}
