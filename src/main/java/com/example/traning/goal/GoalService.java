package com.example.traning.goal;

import com.example.traning.pr.dao.PersonalRecordDao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

  private final GoalDao goalDao;
  private final PersonalRecordDao personalRecordDao;

  public GoalService(GoalDao goalDao, PersonalRecordDao personalRecordDao) {
    this.goalDao = goalDao;
    this.personalRecordDao = personalRecordDao;
  }

  @Transactional(readOnly = true)
  public List<GoalWithProgress> listGoalsWithProgress(Long userId) {
    List<TrainingGoal> goals = goalDao.selectByUserId(userId);
    LocalDate today = LocalDate.now();

    return goals.stream()
        .map(
            goal -> {
              String effectiveStatus = goal.getStatus();
              if ("ACTIVE".equals(effectiveStatus) && goal.getTargetDate().isBefore(today)) {
                effectiveStatus = "EXPIRED";
              }

              Double currentWeight = null;
              Integer progressPct = null;
              if (goal.getTargetWeight() != null) {
                currentWeight =
                    personalRecordDao
                        .selectByUserIdAndItem(userId, goal.getItemName())
                        .map(pr -> pr.getMaxWeight())
                        .orElse(null);

                if (currentWeight != null
                    && goal.getTargetWeight().compareTo(BigDecimal.ZERO) > 0) {
                  int pct = (int) (currentWeight / goal.getTargetWeight().doubleValue() * 100);
                  progressPct = Math.min(pct, 100);
                }
              }

              return new GoalWithProgress(goal, effectiveStatus, currentWeight, progressPct);
            })
        .toList();
  }

  @Transactional
  public void createGoal(Long userId, GoalForm form) {
    if (form.getTargetWeight() == null && form.getTargetReps() == null) {
      throw new IllegalArgumentException("目標重量または目標回数のどちらかは必須です");
    }
    TrainingGoal goal = new TrainingGoal();
    goal.setUserId(userId);
    goal.setItemName(form.getItemName());
    goal.setTargetWeight(form.getTargetWeight());
    goal.setTargetReps(form.getTargetReps());
    goal.setTargetDate(form.getTargetDate());
    goal.setStatus("ACTIVE");
    goal.setMemo(form.getMemo());
    goal.setCreatedAt(LocalDateTime.now());
    goal.setUpdatedAt(LocalDateTime.now());
    goalDao.insert(goal);
  }

  @Transactional
  public void achieveGoal(Long goalId, Long userId) {
    TrainingGoal goal =
        goalDao
            .selectById(goalId)
            .filter(g -> g.getUserId().equals(userId))
            .orElseThrow(() -> new RuntimeException("目標が見つかりません id:" + goalId));
    goal.setStatus("ACHIEVED");
    goal.setUpdatedAt(LocalDateTime.now());
    goalDao.update(goal);
  }

  @Transactional
  public void deleteGoal(Long goalId, Long userId) {
    TrainingGoal goal =
        goalDao
            .selectById(goalId)
            .filter(g -> g.getUserId().equals(userId))
            .orElseThrow(() -> new RuntimeException("目標が見つかりません id:" + goalId));
    goalDao.delete(goal);
  }

  @Transactional(readOnly = true)
  public List<GoalWithProgress> listGoalsForAdmin(Long userId) {
    return listGoalsWithProgress(userId);
  }
}
