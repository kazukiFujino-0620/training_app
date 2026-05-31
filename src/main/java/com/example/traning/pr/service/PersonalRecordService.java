package com.example.traning.pr.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.traning.pr.PersonalRecord;
import com.example.traning.pr.dao.PersonalRecordDao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalRecordService {

    private final PersonalRecordDao personalRecordDao;

    public void updateIfBetter(Long userId, String itemName,
                               Double weight, Integer reps, LocalDate date) {
        try {
            Optional<PersonalRecord> existing =
                personalRecordDao.selectByUserIdAndItem(userId, itemName);

            if (existing.isEmpty()) {
                PersonalRecord pr = new PersonalRecord();
                pr.setUserId(userId);
                pr.setItemName(itemName);
                pr.setMaxWeight(weight != null ? weight : 0.0);
                pr.setMaxReps(reps != null ? reps : 0);
                pr.setAchievedDate(date);
                personalRecordDao.insert(pr);
            } else {
                PersonalRecord pr = existing.get();
                boolean updated = false;

                if (weight != null && weight > pr.getMaxWeight()) {
                    pr.setMaxWeight(weight);
                    updated = true;
                }
                if (reps != null && reps > pr.getMaxReps()) {
                    pr.setMaxReps(reps);
                    updated = true;
                }
                if (updated) {
                    pr.setAchievedDate(date);
                    personalRecordDao.update(pr);
                }
            }
        } catch (Exception e) {
            log.warn("PR更新中にエラー: userId={}, itemName={}, message={}",
                     userId, itemName, e.getMessage());
        }
    }

    public List<PersonalRecord> getByUserId(Long userId) {
        return personalRecordDao.selectByUserId(userId);
    }
}
