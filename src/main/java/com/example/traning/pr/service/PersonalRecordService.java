package com.example.traning.pr.service;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.dao.PersonalRecordDao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

  private final PersonalRecordDao personalRecordDao;

  /**
   * 重量・回数は同一セットの実測値として連動して更新する（重量優先、同重量なら回数が多い方を採用）。 どちらか一方だけを個別に上書きすると、実際には達成していない重量×回数の組み合わせが
   * 表示されてしまうため、必ずペアで比較・更新する。
   */
  @Transactional
  public void updateIfBetter(
      Long userId, String itemName, Double weight, Integer reps, LocalDate date) {
    if (weight == null || reps == null) {
      return;
    }
    try {
      Optional<PersonalRecord> existing = personalRecordDao.selectByUserIdAndItem(userId, itemName);

      if (existing.isEmpty()) {
        PersonalRecord pr = new PersonalRecord();
        pr.setUserId(userId);
        pr.setItemName(itemName);
        pr.setMaxWeight(weight);
        pr.setMaxReps(reps);
        pr.setAchievedDate(date);
        personalRecordDao.insert(pr);
        return;
      }

      PersonalRecord pr = existing.get();
      int weightCompare = Double.compare(weight, pr.getMaxWeight());
      boolean isBetter = weightCompare > 0 || (weightCompare == 0 && reps > pr.getMaxReps());
      if (isBetter) {
        pr.setMaxWeight(weight);
        pr.setMaxReps(reps);
        pr.setAchievedDate(date);
        personalRecordDao.update(pr);
      }
    } catch (Exception e) {
      log.warn("PR更新中にエラー: userId={}, itemName={}, message={}", userId, itemName, e.getMessage());
    }
  }

  public List<PersonalRecord> getByUserId(Long userId) {
    return personalRecordDao.selectByUserId(userId);
  }

  public Optional<PersonalRecord> getByUserIdAndItem(Long userId, String itemName) {
    return personalRecordDao.selectByUserIdAndItem(userId, itemName);
  }
}
