package com.example.traning.weekly;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeeklyProgramService {

  private final WeeklyProgramDao weeklyProgramDao;

  private static final String[] DAY_CODES = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

  @Transactional(readOnly = true)
  public Map<String, WeeklyProgram> getSettingsMap(Long userId) {
    return weeklyProgramDao.selectByUserId(userId).stream()
        .collect(Collectors.toMap(WeeklyProgram::getDayOfWeek, p -> p));
  }

  @Transactional
  public void saveSettings(
      Long userId, Map<String, String> dayToPart, Map<String, Long> dayToTemplate) {
    weeklyProgramDao.deleteByUserId(userId);
    LocalDateTime now = LocalDateTime.now();
    for (String dayCode : DAY_CODES) {
      String partCode = dayToPart.getOrDefault(dayCode, "");
      if (partCode == null || partCode.isBlank()) continue;
      WeeklyProgram program = new WeeklyProgram();
      program.setUserId(userId);
      program.setDayOfWeek(dayCode);
      program.setPartCode(partCode);
      Long templateId = dayToTemplate.get(dayCode);
      program.setTemplateId((templateId != null && templateId > 0) ? templateId : null);
      program.setCreatedAt(now);
      program.setUpdatedAt(now);
      weeklyProgramDao.insert(program);
    }
  }

  @Transactional(readOnly = true)
  public Optional<WeeklyProgram> getTodayProgram(Long userId) {
    String todayCode = toDayCode(LocalDate.now().getDayOfWeek());
    return weeklyProgramDao.selectByUserId(userId).stream()
        .filter(p -> todayCode.equals(p.getDayOfWeek()))
        .findFirst();
  }

  private String toDayCode(DayOfWeek dow) {
    return switch (dow) {
      case MONDAY -> "MON";
      case TUESDAY -> "TUE";
      case WEDNESDAY -> "WED";
      case THURSDAY -> "THU";
      case FRIDAY -> "FRI";
      case SATURDAY -> "SAT";
      case SUNDAY -> "SUN";
    };
  }

  public List<WeeklyProgram> getAllSettings(Long userId) {
    return weeklyProgramDao.selectByUserId(userId);
  }
}
