package com.example.traning.user.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.traning.dao.UserDao;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

	private final UserDao userDao;
	private final TrainingDao trainingDao;

	public List<User> findAll() {
		log.info("全ユーザー情報の取得を開始します。");
		return userDao.selectAll();
	}

	public boolean updateUserInfo(User user) {
		boolean result = userDao.update(user).getCount() > 0;
		return result;
	}

	public User getUserByEmail(String email) {
		return userDao.selectByEmail(email)
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
		String escaped = userName
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
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
			return dateList == null ? List.of()
					: dateList.stream().map(d -> "NONE").collect(java.util.stream.Collectors.toList());
		}
		List<Training> records = trainingDao.selectByUserIdAndDateRange(
				userId, dateList.get(0), dateList.get(41));

		return dateList.stream()
				.map(date -> checkStatus(date, records))
				.collect(Collectors.toList());
	}

	private String checkStatus(LocalDate date, List<Training> records) {
		List<Training> dailyTrainings = records.stream()
				.filter(t -> t.getTrainingDate().equals(date))
				.collect(Collectors.toList());

		if (dailyTrainings.isEmpty()) {
			return "NONE";
		}

		boolean allDone = dailyTrainings.stream()
				.allMatch(t -> Boolean.TRUE.equals(t.isAllCompleted()));

		return allDone ? "COMPLETED" : "IN_PROGRESS";
	}
}
