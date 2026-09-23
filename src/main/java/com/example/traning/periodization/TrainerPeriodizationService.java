package com.example.traning.periodization;

import com.example.traning.trainer.TrainerAdviceService;
import com.example.traning.user.User;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * トレーナーによる担当ユーザー向け期分けサイクル作成（機能見直し-1-#3 詳細設計書3-1節 createTrainerManagedCycle）。
 *
 * <p>担当関係は既存のトレーナーアドバイスと同じスコープ（{@link TrainerAdviceService#listTrainees}）に加え、 設計書どおり
 * User.assignedTrainerId が操作者本人であることを必須とする。
 */
@Service
@RequiredArgsConstructor
public class TrainerPeriodizationService {

  private final PeriodizationService periodizationService;
  private final TrainerAdviceService trainerAdviceService;

  @Transactional
  public Long createTrainerManagedCycle(
      User trainer, Long targetUserId, Long presetProgramId, LocalDate startDate) {
    if (targetUserId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "対象ユーザーを選択してください");
    }
    long trainerId = trainer.getUserId().longValue();
    User target =
        trainerAdviceService.listTrainees(trainer).stream()
            .filter(u -> u.getUserId().longValue() == targetUserId)
            .filter(u -> u.getAssignedTrainerId() != null && u.getAssignedTrainerId() == trainerId)
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "担当ユーザー以外のプログラムは作成できません"));
    Long userId = target.getUserId().longValue();
    PresetProgram preset = periodizationService.findVisiblePreset(userId, presetProgramId);
    return periodizationService.createCycleFromPreset(
        userId, preset, CycleTier.TRAINER_MANAGED, trainerId, startDate);
  }
}
