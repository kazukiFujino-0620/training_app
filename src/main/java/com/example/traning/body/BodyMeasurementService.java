package com.example.traning.body;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BodyMeasurementService {

    private final BodyMeasurementDao bodyMeasurementDao;

    @Transactional(readOnly = true)
    public List<BodyMeasurement> getAll(Long userId) {
        return bodyMeasurementDao.selectByUserId(userId);
    }

    @Transactional
    public void save(Long userId, LocalDate date, Double weightKg, Double bodyFatPct, String memo) {
        Optional<BodyMeasurement> existing = bodyMeasurementDao.selectByUserIdAndDate(userId, date);
        if (existing.isPresent()) {
            BodyMeasurement m = existing.get();
            m.weightKg = weightKg;
            m.bodyFatPct = bodyFatPct;
            m.memo = memo;
            bodyMeasurementDao.update(m);
        } else {
            BodyMeasurement m = new BodyMeasurement();
            m.userId = userId;
            m.measuredDate = date;
            m.weightKg = weightKg;
            m.bodyFatPct = bodyFatPct;
            m.memo = memo;
            bodyMeasurementDao.insert(m);
        }
    }

    @Transactional
    public void delete(Long id, Long userId) {
        bodyMeasurementDao.deleteById(id, userId);
    }

    @Transactional(readOnly = true)
    public List<BodyMeasurement> getForDateRange(Long userId, LocalDate from, LocalDate to) {
        return bodyMeasurementDao.selectByUserIdAndDateRange(userId, from, to);
    }
}
