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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
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
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Controller
@Validated
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
		User userEntity = trainingService.getUserByName(principal.getName());

		// 2. カレンダーの期間（42日分）を計算
		YearMonth yearMonth = YearMonth.from(selectedDate);
		LocalDate firstDay = yearMonth.atDay(1);
		int firstDayValue = firstDay.getDayOfWeek().getValue();
		LocalDate calendarStart = firstDay.minusDays((long) firstDayValue - 1);
		LocalDate calendarEnd = calendarStart.plusDays(41);

		List<LocalDate> dateList = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			dateList.add(calendarStart.plusDays(i));
		}

		// 3. カレンダー期間内のデータを一括取得
		List<Training> allTrainings = trainingDao.selectByDate(userId, calendarStart, calendarEnd);

		// 4. 日付ごとにMapへ分類
		Map<LocalDate, List<Training>> trainingMap = allTrainings.stream()
				.collect(Collectors.groupingBy(Training::getTrainingDate));

		// 5. 表示する日のデータをMapから取得
		List<Training> trainingList = trainingMap.getOrDefault(selectedDate, new ArrayList<>());

		// マスターデータの一括取得
		List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

		for (Training t : trainingList) {
			t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));
			t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
		}

		// 6. カレンダーのステータス判定
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
		model.addAttribute("selectedDateStr", selectedDate.toString()); // Add formatted string for comparison
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
		Long userId = trainingService.getUserIdByName(principal.getName());
		training.setUserId(userId);

		if (training.getId() == null) {
			training.setCreateDatetime(LocalDateTime.now());
		}
		training.setUpdatedDatetime(LocalDateTime.now());

		trainingService.save(training, principal);

		return "redirect:/menu?date=" + training.getTrainingDate();
	}

	@PostMapping("/menu/delete")
	public String delete(@RequestParam("id") Long id, Principal principal) {
		log.info("削除リクエストが来ました！ ID: {}", id);

		// ★ 所有者チェック: 自分のトレーニングでなければ拒否
		Training training = trainingService.getTrainingById(id);
		if (training == null) {
			log.warn("削除対象が存在しません ID: {}", id);
			return "redirect:/menu";
		}

		Long currentUserId = trainingService.getUserIdByName(principal.getName());
		if (!training.getUserId().equals(currentUserId)) {
			log.warn("不正な削除リクエスト: ユーザー {} がトレーニング {} を削除しようとしました",
					currentUserId, id);
			return "redirect:/menu";
		}

		LocalDate date = training.getTrainingDate();
		trainingService.deleteTraining(id);

		return "redirect:/menu?date=" + date;
	}

	@GetMapping("/api/training-items")
	@ResponseBody
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
	public Long apiSaveTraining(@Valid @RequestBody Training training, Principal principal) {
		training.setUserId(trainingService.getUserIdByName(principal.getName()));

		if (training.getCreateDatetime() == null) {
			training.setCreateDatetime(LocalDateTime.now());
		}
		training.setUpdatedDatetime(LocalDateTime.now());

		trainingService.save(training, principal);

		return training.getId();
	}

	@PostMapping("/api/training/finish")
	@ResponseBody
	public ResponseEntity<String> finishTrainig(@Valid @RequestBody List<Training> trainingList, Principal principal) {
		try {
			// ★ IDOR 対策: ログインユーザーが所有するトレーニングのみ保存を許可
			Long currentUserId = trainingService.getUserIdByName(principal.getName());

			for (Training t : trainingList) {
				// 1. セットデータ確認
				if (t.getDetails() == null || t.getDetails().isEmpty()) {
					return ResponseEntity.badRequest().body("セットデータが空の種目があります。");
				}

				// 2. トレーニングが実在するか確認
				if (t.getId() == null || t.getId() <= 0) {
					log.warn("不正なトレーニングID: {}", t.getId());
					return ResponseEntity.badRequest().body("不正なトレーニングIDです。");
				}

				Training existingTraining = trainingService.getTrainingById(t.getId());
				if (existingTraining == null) {
					log.warn("トレーニングが見つかりません: ID={}", t.getId());
					return ResponseEntity.notFound().build();
				}

				// 3. 所有者確認: 自分のトレーニングでなければ拒否
				if (!existingTraining.getUserId().equals(currentUserId)) {
					log.warn("不正なアクセス検知: ユーザー {} がトレーニング {} を保存しようとしました",
							currentUserId, t.getId());
					return ResponseEntity.status(HttpStatus.FORBIDDEN)
							.body("このトレーニングを変更する権限がありません。");
				}
			}

			trainingService.saveAll(trainingList);
			return ResponseEntity.ok("保存に成功しました");
		} catch (Exception e) {
			log.error("トレーニング保存エラー", e);
			return ResponseEntity.internalServerError().body("登録に失敗しました。" + e.getMessage());
		}
	}

	@GetMapping("/api/training/{id}")
	@ResponseBody
	public ResponseEntity<Training> getTraining(@PathVariable Long id, Principal principal) {
		Training training = trainingService.getTrainingById(id);

		// 存在しない場合
		if (training == null) {
			log.warn("存在しないトレーニングへのアクセス: ID={}", id);
			return ResponseEntity.notFound().build();
		}

		// ★ 所有者チェック: ログインユーザーのデータでなければ 403 を返す
		Long currentUserId = trainingService.getUserIdByName(principal.getName());
		if (!training.getUserId().equals(currentUserId)) {
			log.warn("不正アクセス検知: ユーザー {} がトレーニング {} へアクセスしようとしました",
					currentUserId, id);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		return ResponseEntity.ok(training);
	}

	@PostMapping("/api/training/update/{id}")
	@ResponseBody
	public ResponseEntity<Void> updateTraining(@PathVariable Long id, @Valid @RequestBody Training training,
			Principal principal) {
		Training existingTraining = trainingService.getTrainingById(id);
		if (existingTraining == null) {
			return ResponseEntity.notFound().build();
		}

		training.setId(id);
		training.setUserId(existingTraining.getUserId());
		training.setTrainingDate(existingTraining.getTrainingDate());
		training.setPartCode(existingTraining.getPartCode());
		training.setCreateDatetime(existingTraining.getCreateDatetime());

		Long currentUserId = trainingService.getUserIdByName(principal.getName());
		if (!training.getUserId().equals(currentUserId)) {
			log.warn("不正な更新リクエスト: ユーザー {} がトレーニング {} を更新しようとしました",
					currentUserId, id);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		trainingService.save(training, principal);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/training/delete/{id}")
	@ResponseBody
	public ResponseEntity<Void> deleteTraining(@PathVariable Long id, Principal principal) {
		Training existingTraining = trainingService.getTrainingById(id);
		if (existingTraining == null) {
			return ResponseEntity.notFound().build();
		}

		Long currentUserId = trainingService.getUserIdByName(principal.getName());
		if (!existingTraining.getUserId().equals(currentUserId)) {
			log.warn("不正な削除リクエスト: ユーザー {} がトレーニング {} を削除しようとしました",
					currentUserId, id);
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		trainingService.deleteTraining(id);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/training/register")
	public String trainingRegister(@RequestParam(name = "date", required = false) String dateStr, Model model,
			Principal principal) {
		LocalDate today = LocalDate.now();
		LocalDate selectedDate = (dateStr != null) ? LocalDate.parse(dateStr) : today;
		Long userId = trainingService.getUserIdByName(principal.getName());

		List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

		model.addAttribute("selectedDate", selectedDate);
		model.addAttribute("userId", userId);
		model.addAttribute("partList", partList);

		return "training/training-register";
	}

	@GetMapping("/api/training-parts")
	@ResponseBody
	public List<TrainingMaster> getTrainingParts() {
		return trainingMasterDao.selectAllParts();
	}

	@GetMapping("/api/training-items-grouped")
	@ResponseBody
	public Map<String, List<TrainingItemMaster>> getTrainingItemsGrouped() {
		List<TrainingMaster> parts = trainingMasterDao.selectAllParts();
		Map<String, List<TrainingItemMaster>> groupedItems = new java.util.HashMap<>();

		for (TrainingMaster part : parts) {
			List<TrainingItemMaster> items = trainingMasterDao.selectItemsByPart(part.getPartCode());
			groupedItems.put(part.getPartCode(), items);
		}

		return groupedItems;
	}

	@PostMapping("/api/training/register-bulk")
	@ResponseBody
	public ResponseEntity<String> registerBulkTraining(
			@RequestBody Map<String, Object> data,
			Principal principal) {
		try {
			String dateStr = (String) data.get("date");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> trainingsData = (List<Map<String, Object>>) data.get("trainings");

			LocalDate trainingDate = LocalDate.parse(dateStr);
			Long userId = trainingService.getUserIdByName(principal.getName());

			for (Map<String, Object> trainingMap : trainingsData) {
				Training training = new Training();
				training.setUserId(userId);
				training.setTrainingDate(trainingDate);
				training.setMenu((String) trainingMap.get("menu"));
				training.setPartCode((String) trainingMap.get("partCode"));
				training.setIsCompleted(false);
				training.setIsAllCompleted(false);
				training.setCreateDatetime(LocalDateTime.now());
				training.setUpdatedDatetime(LocalDateTime.now());

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> detailsData = (List<Map<String, Object>>) trainingMap.get("details");
				List<TrainingDetail> details = new ArrayList<>();

				for (int i = 0; i < detailsData.size(); i++) {
					Map<String, Object> detailMap = detailsData.get(i);
					TrainingDetail detail = new TrainingDetail();
					detail.setWeight(((Number) detailMap.get("weight")).doubleValue());
					detail.setReps(((Number) detailMap.get("reps")).intValue());
					detail.setSetNumber(i + 1);
					detail.setCompleted((Boolean) detailMap.getOrDefault("isCompleted", false));
					details.add(detail);
				}

				training.setDetails(details);
				trainingService.save(training, principal);
			}

			return ResponseEntity.ok("保存に成功しました");
		} catch (Exception e) {
			log.error("一括登録エラー", e);
			return ResponseEntity.internalServerError().body("登録に失敗しました: " + e.getMessage());
		}
	}
}