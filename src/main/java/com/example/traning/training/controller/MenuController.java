package com.example.traning.training.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;

import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class MenuController {

	private final TrainingDao trainingDao;
	private final TrainingDetailDao trainingDetailDao;
	private final TrainingMasterDao trainingMasterDao;
	private final TrainingService trainingService;

	public MenuController(TrainingDao trainingDao, TrainingDetailDao trainingDetailDao,
			TrainingMasterDao trainingMasterDao, TrainingService trainingService) {
		this.trainingDao = trainingDao;
		this.trainingDetailDao = trainingDetailDao;
		this.trainingMasterDao = trainingMasterDao;
		this.trainingService = trainingService;
	}

	@GetMapping("/menu")
	public String menu(@RequestParam(name = "date", required = false) String dateStr, Model model,
			Principal principal) {
		LocalDate today = LocalDate.now();

		Long userId = trainingService.getUserIdByName(principal.getName());
		log.debug("ログインユーザーのIDは: {}", userId);

		// 1. 日付の決定
		LocalDate selectedDate = (dateStr != null) ? LocalDate.parse(dateStr) : today;
		User userEntity = trainingService.getUserByName(userId);

		// 2. カレンダーの期間（42日分）を計算
		YearMonth yearMonth = YearMonth.from(selectedDate);
		LocalDate firstDay = yearMonth.atDay(1);
		int firstDayValue = firstDay.getDayOfWeek().getValue();
		// 月曜始まりの場合の調整
		LocalDate calendarStart = firstDay.minusDays((long) firstDayValue - 1);
		LocalDate calendarEnd = calendarStart.plusDays(41);

		List<LocalDate> dateList = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			dateList.add(calendarStart.plusDays(i));
		}

		// 3. 【一括取得】カレンダー期間内のデータを1回のSQLで取得
		// ※Daoに新設する selectByPeriod メソッドを使用
		List<Training> allTrainings = trainingDao.selectByDate(userId, calendarStart, calendarEnd);

		// 4. 【効率化】取得したデータを日付ごとにMapへ分類（Java側で処理）
		Map<LocalDate, List<Training>> trainingMap = allTrainings.stream()
				.collect(Collectors.groupingBy(Training::getTrainingDate));

		// 5. 表示する日のデータ（trainingList）をMapから取得
		List<Training> trainingList = trainingMap.getOrDefault(selectedDate, new ArrayList<>());

		// マスターデータの一括取得
		List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

		// 選択された日のトレーニング詳細をセット（ここも本来は一括取得が理想ですが、まずは1日分のみに限定）
		for (Training t : trainingList) {
			t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
			t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
		}

		// 6. カレンダーのステータス判定（SQL発行はゼロ！）
		List<String> dayStatusList = new ArrayList<>();
		for (LocalDate date : dateList) {
			List<Training> dailyTrainings = trainingMap.getOrDefault(date, Collections.emptyList());
			if (dailyTrainings.isEmpty()) {
				dayStatusList.add("NONE");
			} else {
				boolean allDone = dailyTrainings.stream().allMatch(Training::isAllCompleted);
				dayStatusList.add(allDone ? "COMPLETED" : "IN_PROGRESS");
			}
		}

		// 7. 集計とModelセット
		long totalCount = trainingList.size();
		long completedCount = trainingList.stream().filter(Training::isAllCompleted).count();

		model.addAttribute("loginUser", userEntity);
		model.addAttribute("targetMonth", yearMonth);
		model.addAttribute("dateList", dateList);
		model.addAttribute("today", today);
		model.addAttribute("selectedDate", selectedDate);
		model.addAttribute("trainingList", trainingList);
		model.addAttribute("prevMonth", yearMonth.minusMonths(1).atDay(1));
		model.addAttribute("nextMonth", yearMonth.plusMonths(1).atDay(1));
		model.addAttribute("partList", partList);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("completedCount", completedCount);
		model.addAttribute("isDailyCompleted", totalCount > 0 && totalCount == completedCount);
		model.addAttribute("dayStatusList", dayStatusList);

		return "menu";
	}

	@PostMapping("/menu/save")
	public String save(@ModelAttribute Training training, Principal principal) {
		// 1. ログインユーザーのIDをセット（本来はService等で取得）
		// ※今回は簡易的にログイン名をLongに変換するか、固定値を振る想定
		Long userId = trainingService.getUserIdByName(principal.getName());
		training.setUserId(userId);

		// 2. IDの有無で新規登録か更新かを判断
		if (training.getId() == null) {
			training.setCreateDatetime(LocalDateTime.now());
		}
		training.setUpdatedDatetime(LocalDateTime.now());

		trainingService.save(training, principal);

		// 3. メニュー画面にリダイレクト
		return "redirect:/menu?date=" + training.getTrainingDate();
	}

	@PostMapping("/delete")
	public String delete(@RequestParam("id") Long id) {
		log.info("削除リクエストが来ました！ ID: {}", id);
		trainingService.deleteTraining(id);
		return "redirect:/menu";
	}

	@GetMapping("/api/training-items")
	@ResponseBody // これを付けると、ListがそのままJSON形式でJavaScriptに渡ります
	public List<TrainingItemMaster> getItems(@RequestParam String partCode) {
		return trainingMasterDao.selectItemsByPart(partCode);
	}

	@GetMapping("/start/training")
	public String startTraining(@RequestParam("date") String dateStr, Model model, Principal principal) {
		LocalDate selectedDate = LocalDate.parse(dateStr);
		Long userId = trainingService.getUserIdByName(principal.getName());
		List<Training> trainingList = trainingService.getFullTrainingData(userId, LocalDate.parse(dateStr));
		List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

		model.addAttribute("selectedDate", selectedDate);
		model.addAttribute("trainingList", trainingList);
		model.addAttribute("partList", partList);

		return "training/start_training";
	}

	@PostMapping("/api/training/save")
	@ResponseBody
	public Long apiSaveTraining(@RequestBody Training training, Principal princilal) {
		// ユーザーIDをセット
		training.setUserId(trainingService.getUserIdByName(princilal.getName()));

		// 日付時間をセット
		if (training.getCreateDatetime() == null) {
			training.setCreateDatetime(LocalDateTime.now());
		}
		training.setUpdatedDatetime(LocalDateTime.now());

		// Serviceクラスを呼び出す
		trainingService.save(training, princilal);

		return training.getId();
	}

	@PostMapping("/api/training/finish")
	@ResponseBody
	public ResponseEntity<String> finishTrainig(@RequestBody List<Training> trainingList) {
		try {
			for (Training t : trainingList) {
				if (t.getDetails() == null || t.getDetails().isEmpty()) {
					return ResponseEntity.badRequest().body("セットデータがからの種目があります。");
				}
			}
			trainingService.saveAll(trainingList);

			return ResponseEntity.ok("保存に成功しました");
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("登録に失敗しました。" + e.getMessage());
		}

	}

	@PostMapping("/api/training/delete/{id}")
	@ResponseBody
	public ResponseEntity<Void> deleteTraining(@PathVariable Long id) {
		trainingService.deleteTraining(id);
		return ResponseEntity.ok().build();
	}
}