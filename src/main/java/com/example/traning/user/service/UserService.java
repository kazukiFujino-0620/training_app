package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

  private final UserDao userDao;
  private final TrainingDao trainingDao;
  private final UserSessionInvalidator userSessionInvalidator;

  public List<User> findAll() {
    log.info("全ユーザー情報の取得を開始します。");
    return userDao.selectAll();
  }

  /**
   * ユーザー情報を更新する。役割が変わった場合は、そのユーザーのログイン状態（Webセッション・モバイルのトークン）を無効にし、 次の操作でログインし直してもらう（itバグ-48:
   * 変更前の権限のまま使い続けられないようにする）。
   */
  @Transactional
  public boolean updateUserInfo(User user) {
    User before = userDao.selectById(user.getUserId());
    boolean result = userDao.update(user).getCount() > 0;
    if (result && before != null && !Objects.equals(before.getRole(), user.getRole())) {
      userSessionInvalidator.invalidateAll(before);
    }
    return result;
  }

  /** 機能見直し-1-#15: 役割の確認が必要かどうか（移行対象の印）を更新する。 */
  public void updateRoleReviewRequired(Integer userId, boolean roleReviewRequired) {
    userDao.updateRoleReviewRequired(userId, roleReviewRequired);
  }

  public User getUserByEmail(String email) {
    return userDao
        .selectByEmail(email)
        .orElseThrow(() -> new RuntimeException("ユーザーが見つかりません email:" + email));
  }

  public User getUserById(Integer id) {
    log.info("ユーザーID: {} の取得を開始します。", id);
    User user = userDao.selectById(id);

    if (user == null) {
      throw new RuntimeException("ユーザーが見つかりません ID:" + id);
    }
    return user;
  }

  public List<User> searchUsers(String userName) {
    log.debug("ユーザー名検索開始");
    if (userName == null || userName.trim().isEmpty()) {
      return userDao.selectAll();
    }
    // LIKE 特殊文字（% _ \）をエスケープしてワイルドカードインジェクションを防ぐ
    String escaped = userName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    String searchWord = "%" + escaped + "%";
    return userDao.selectByName(searchWord);
  }

  public List<LocalDate> generateCalendarDates(LocalDate targetMonth) {
    log.info("カレンダー生成: {}", targetMonth);
    List<LocalDate> dates = new ArrayList<>();
    LocalDate firstDay = targetMonth.withDayOfMonth(1);
    int dayOfWeek = firstDay.getDayOfWeek().getValue();
    LocalDate startDate = firstDay.minusDays(dayOfWeek - 1);

    for (int i = 0; i < 42; i++) {
      dates.add(startDate.plusDays(i));
    }
    return dates;
  }

  public List<String> getDayStatusList(Integer userId, List<LocalDate> dateList) {
    log.info("ユーザーID: {} の進捗ステータスを取得します。", userId);
    if (dateList == null || dateList.size() < 42) {
      log.warn("カレンダー日付リストが不正です: size={}", dateList == null ? 0 : dateList.size());
      return dateList == null
          ? List.of()
          : dateList.stream().map(d -> "NONE").collect(java.util.stream.Collectors.toList());
    }
    List<Training> records =
        trainingDao.selectByUserIdAndDateRange(userId, dateList.get(0), dateList.get(41));

    return dateList.stream().map(date -> checkStatus(date, records)).collect(Collectors.toList());
  }

  private String checkStatus(LocalDate date, List<Training> records) {
    List<Training> dailyTrainings =
        records.stream().filter(t -> t.getTrainingDate().equals(date)).collect(Collectors.toList());

    if (dailyTrainings.isEmpty()) {
      return "NONE";
    }

    boolean allDone =
        dailyTrainings.stream().allMatch(t -> Boolean.TRUE.equals(t.isAllCompleted()));

    return allDone ? "COMPLETED" : "IN_PROGRESS";
  }
}
