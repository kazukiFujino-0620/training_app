package com.example.traning.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.dao.training.TrainingDao;
import com.example.traning.entity.User;
import com.example.traning.training.entity.Training;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserDao userDao;
    private final TrainingDao trainingDao;

    /**
     * 全ユーザーを取得する
     */
    public List<User> findAll() {
        log.info("全ユーザー情報の取得を開始します。");
        return userDao.selectAll();
    }

    /**
     * ユーザー情報を更新する（今後、権限変更などで使用）
     */
    public boolean updateUserInfo(User user) {
        boolean result = userDao.update(user).getCount() > 0;

        return result;
    }

    public User getUserById(Integer id) {
        log.info("ユーザーID: {} の取得を開始します。", id);
        // Doma2のDao経由で取得
        User user = userDao.selectById(id);

        if (user == null) {
            throw new RuntimeException("ユーザーが見つかりません ID:" + id);
        }
        return user;
    }

    public List<User> searchUsers(String userName) {
        log.info("ユーザー名で検索を開始します。検索ワード: {}", userName);
        if (userName == null || userName.trim().isEmpty()) {
            return userDao.selectAll();
        }

        String searchWord = "%" + userName + "%";
        return userDao.selectByName(searchWord);
    }

    /**
     * 指定された月のカレンダー表示用日付リスト（前後月含む42日分）を生成する
     */
    public List<LocalDate> generateCalendarDates(LocalDate targetMonth) {
        log.info("カレンダー生成: {}", targetMonth);
        List<LocalDate> dates = new ArrayList<>();

        // 月の初日の曜日を確認し、カレンダーの左上（先月の終わり）を特定
        LocalDate firstDay = targetMonth.withDayOfMonth(1);
        int dayOfWeek = firstDay.getDayOfWeek().getValue(); // 1(月)〜7(日)

        // 月曜日開始のカレンダーの場合
        LocalDate startDate = firstDay.minusDays(dayOfWeek - 1);

        for (int i = 0; i < 42; i++) {
            dates.add(startDate.plusDays(i));
        }
        return dates;
    }

    /**
     * 各日付のトレーニング進捗ステータスを取得する
     */
    public List<String> getDayStatusList(Integer userId, List<LocalDate> dateList) {
        log.info("ユーザーID: {} の進捗ステータスを取得します。", userId);

        // 1ヶ月分（42日分）のデータを一括取得
        // ※変数名は定義したDaoの名前に合わせてください（例: trainingDao）
        List<Training> records = trainingDao.selectByUserIdAndDateRange(
                userId, dateList.get(0), dateList.get(41));

        return dateList.stream()
                .map(date -> checkStatus(date, records))
                .collect(Collectors.toList());
    }

    private String checkStatus(LocalDate date, List<Training> records) {
        // その日の種目をフィルタリング
        List<Training> dailyTrainings = records.stream()
                .filter(t -> t.getTrainingDate().equals(date))
                .collect(Collectors.toList());

        if (dailyTrainings.isEmpty()) {
            return "NONE";
        }

        // 全ての種目が完了しているか判定
        // booleanの命名が is_all_completed の場合、Lombok等では getIsAllCompleted() になることが多いです
        boolean allDone = dailyTrainings.stream()
                .allMatch(t -> Boolean.TRUE.equals(t.isAllCompleted()));

        return allDone ? "COMPLETED" : "IN_PROGRESS";
    }
}