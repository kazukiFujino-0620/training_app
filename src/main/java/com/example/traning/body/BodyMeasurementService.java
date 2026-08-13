package com.example.traning.body;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
      // sourceはDB上NOT NULL。DomaのINSERTは全列を明示的に送信するため、
      // 未設定のままだとNULLが明示的にバインドされDBのDEFAULT句が効かず登録に失敗する。
      m.source = "MANUAL";
      bodyMeasurementDao.insert(m);
    }
  }

  /** HealthKit/Health Connect経由の体重同期用。既存の同日データがあれば同期元を問わず上書きする。 */
  @Transactional
  public void syncFromHealthPlatform(
      Long userId, LocalDate date, Double weightKg, Double bodyFatPct, String source) {
    Optional<BodyMeasurement> existing = bodyMeasurementDao.selectByUserIdAndDate(userId, date);
    if (existing.isPresent()) {
      BodyMeasurement m = existing.get();
      m.weightKg = weightKg;
      m.bodyFatPct = bodyFatPct;
      m.source = source;
      bodyMeasurementDao.update(m);
    } else {
      BodyMeasurement m = new BodyMeasurement();
      m.userId = userId;
      m.measuredDate = date;
      m.weightKg = weightKg;
      m.bodyFatPct = bodyFatPct;
      m.source = source;
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

  @Transactional(readOnly = true)
  public Optional<BodyMeasurement> getLatest(Long userId) {
    return bodyMeasurementDao.selectLatestByUserId(userId);
  }
}
