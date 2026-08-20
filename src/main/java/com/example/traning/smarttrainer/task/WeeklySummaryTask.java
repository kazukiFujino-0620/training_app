package com.example.traning.smarttrainer.task;

import com.example.traning.common.SummaryMailService;
import com.example.traning.dao.UserDao;
import com.example.traning.line.LineMessagingService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.dao.TrainingDetailDao.PartVolume;
import com.example.traning.user.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklySummaryTask {

  private final UserDao userDao;
  private final TrainingDao trainingDao;
  private final TrainingDetailDao trainingDetailDao;
  private final SummaryMailService summaryMailService;
  private final LineMessagingService lineMessagingService;

  @Scheduled(cron = "${batch.summary.weekly.cron}")
  public void sendWeeklySummary() {
    LocalDate today = LocalDate.now();
    LocalDate weekEnd = today.minusDays(1);
    LocalDate weekStart = weekEnd.with(DayOfWeek.MONDAY);
    LocalDate prevEnd = weekStart.minusDays(1);
    LocalDate prevStart = prevEnd.with(DayOfWeek.MONDAY);

    log.info("週次サマリーバッチ開始 - 対象期間: {} 〜 {}", weekStart, weekEnd);

    List<User> users = userDao.selectAll();
    int success = 0;
    int skip = 0;

    for (User user : users) {
      try {
        Long userId = user.getUserId().longValue();

        int sessionCount = trainingDao.countByUserIdAndDateRange(userId, weekStart, weekEnd);
        Double volume =
            trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, weekStart, weekEnd);
        Double prevVolume =
            trainingDetailDao.selectTotalVolumeByUserIdAndDateRange(userId, prevStart, prevEnd);
        List<PartVolume> partVolumeList =
            trainingDetailDao.selectVolumeByPartAndDateRange(userId, weekStart, weekEnd);
        Map<String, Double> partVolumes = new LinkedHashMap<>();
        for (PartVolume pv : partVolumeList) {
          partVolumes.put(pv.partCode, pv.totalVolume);
        }
        Double changePercent = calcChangePercent(volume, prevVolume);
        double totalVolume = volume != null ? volume : 0.0;

        boolean lineSent = false;
        if (wantsLine(user) && canSendLine(user)) {
          lineMessagingService.sendWeeklySummary(
              user.lineId,
              user.userName,
              weekStart,
              weekEnd,
              sessionCount,
              totalVolume,
              partVolumes,
              changePercent);
          lineSent = true;
        }
        // LINE希望でも未設定・未友だち追加の間はメールにフォールバックし、通知が一切届かない状態を避ける
        if (wantsEmail(user) || !lineSent) {
          summaryMailService.sendWeeklySummary(
              user.email, user.userName, weekStart, weekEnd, sessionCount, totalVolume, partVolumes, changePercent);
        }
        success++;
      } catch (Exception e) {
        log.warn("週次サマリー送信スキップ - userId={}, reason={}", user.getUserId(), e.getMessage());
        skip++;
      }
    }

    log.info("週次サマリーバッチ完了 - 成功: {}, スキップ: {}", success, skip);
  }

  private Double calcChangePercent(Double current, Double prev) {
    if (prev == null || prev == 0.0) return null;
    double cur = current != null ? current : 0.0;
    return (cur - prev) / prev * 100.0;
  }

  private boolean wantsEmail(User user) {
    return !"LINE".equals(user.notificationMethod);
  }

  private boolean wantsLine(User user) {
    return "LINE".equals(user.notificationMethod) || "BOTH".equals(user.notificationMethod);
  }

  private boolean canSendLine(User user) {
    return user.lineId != null
        && Boolean.TRUE.equals(user.lineFriendAdded)
        && lineMessagingService.isConfigured();
  }
}
