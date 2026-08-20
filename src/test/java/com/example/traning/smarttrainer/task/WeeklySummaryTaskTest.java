package com.example.traning.smarttrainer.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.common.SummaryMailService;
import com.example.traning.dao.UserDao;
import com.example.traning.line.LineMessagingService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ita4-1: {@link WeeklySummaryTask}の通知方法（メール/LINE/両方）の出し分け・フォールバック判定を検証する。
 * DAO/各Serviceは全てMockitoでモックし、DB・実際の送信には依存しない。
 */
@ExtendWith(MockitoExtension.class)
class WeeklySummaryTaskTest {

  @Mock private UserDao userDao;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private SummaryMailService summaryMailService;
  @Mock private LineMessagingService lineMessagingService;

  private WeeklySummaryTask task;

  @BeforeEach
  void setUp() {
    task =
        new WeeklySummaryTask(
            userDao, trainingDao, trainingDetailDao, summaryMailService, lineMessagingService);
    when(trainingDetailDao.selectVolumeByPartAndDateRange(any(), any(), any())).thenReturn(List.of());
  }

  private User buildUser(String notificationMethod, String lineId, Boolean lineFriendAdded) {
    User user = new User();
    user.setUserId(1);
    user.setEmail("test@example.com");
    user.setUserName("テスト太郎");
    user.setNotificationMethod(notificationMethod);
    user.setLineId(lineId);
    user.setLineFriendAdded(lineFriendAdded);
    return user;
  }

  @Test
  void 通知方法EMAILならメールのみ送信しLINEは送信しない() {
    when(userDao.selectAll()).thenReturn(List.of(buildUser("EMAIL", null, null)));

    task.sendWeeklySummary();

    verify(summaryMailService).sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
    verify(lineMessagingService, never())
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }

  @Test
  void 通知方法LINEかつ友だち追加済みならLINEのみ送信しメールは送信しない() {
    when(lineMessagingService.isConfigured()).thenReturn(true);
    when(userDao.selectAll()).thenReturn(List.of(buildUser("LINE", "U123", true)));

    task.sendWeeklySummary();

    verify(lineMessagingService)
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
    verify(summaryMailService, never())
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }

  @Test
  void 通知方法LINEでも友だち未追加ならメールにフォールバックする() {
    when(userDao.selectAll()).thenReturn(List.of(buildUser("LINE", "U123", false)));

    task.sendWeeklySummary();

    verify(summaryMailService).sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
    verify(lineMessagingService, never())
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }

  @Test
  void 通知方法LINEでもチャネル未設定ならメールにフォールバックする() {
    when(lineMessagingService.isConfigured()).thenReturn(false);
    when(userDao.selectAll()).thenReturn(List.of(buildUser("LINE", "U123", true)));

    task.sendWeeklySummary();

    verify(summaryMailService).sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }

  @Test
  void 通知方法BOTHかつ友だち追加済みなら両方送信する() {
    when(lineMessagingService.isConfigured()).thenReturn(true);
    when(userDao.selectAll()).thenReturn(List.of(buildUser("BOTH", "U123", true)));

    task.sendWeeklySummary();

    verify(summaryMailService).sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
    verify(lineMessagingService)
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }

  @Test
  void notificationMethod未設定nullは従来通りメールのみ送信する() {
    when(userDao.selectAll()).thenReturn(List.of(buildUser(null, null, null)));

    task.sendWeeklySummary();

    verify(summaryMailService, times(1))
        .sendWeeklySummary(anyString(), anyString(), any(), any(), anyInt(), anyDouble(), any(), any());
  }
}
