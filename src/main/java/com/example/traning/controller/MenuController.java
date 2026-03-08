package com.example.traning.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.training.TrainingDao;
import com.example.traning.dao.training.TrainingDetailDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;
import com.example.traning.training.entity.Training;
import com.example.traning.training.service.TrainingService;

@Controller
public class MenuController {

	@Autowired
	private TrainingDao trainingDao;

	@Autowired
	private TrainingDetailDao trainingDetailDao;

	@Autowired
	private TrainingMasterDao trainingMasterDao;

	@Autowired
	private TrainingService trainingService;

	@GetMapping("/menu")
	public String menu(@RequestParam(name = "date", required = false) String dateStr, Model model) {
		LocalDate today = LocalDate.now();

		// 1. 選択された日付（パラメータがなければ今日）を決定
		LocalDate selectedDate = (dateStr != null) ? LocalDate.parse(dateStr) : today;

		// 2. カレンダー生成ロジック
		YearMonth yearMonth = YearMonth.from(selectedDate); // 選択された日付の月を表示
		LocalDate firstDay = yearMonth.atDay(1);
		int firstDayValue = firstDay.getDayOfWeek().getValue();
		LocalDate calendarStart = firstDay.minusDays(firstDayValue - 1);

		List<LocalDate> dateList = new ArrayList<>();
		for (int i = 0; i < 42; i++) {
			dateList.add(calendarStart.plusDays(i));
		}

		// 3. DBからその日のトレーニングリストを取得 (ユーザーIDは一旦1L固定)
		List<Training> trainingList = trainingDao.selectByDate(1L, selectedDate);

		List<TrainingMaster> partList = trainingMasterDao.selectAllParts();

		for (Training t : trainingList) {
			// 各トレーニングIDに紐づく詳細をセットする
			t.setDetails(trainingDetailDao.selectByTrainingId(t.getId()));

			// 部位コードから日本語名を取得してセット
			t.setPartName(trainingMasterDao.selectNameByCode(t.getPartCode()));
		}

		boolean isDailyCompleted = !trainingList.isEmpty() && trainingList.stream().allMatch(Training::isAllCompleted);

		// 4. Modelへ値をセット
		model.addAttribute("targetMonth", yearMonth);
		model.addAttribute("dateList", dateList);
		model.addAttribute("today", today);
		model.addAttribute("selectedDate", selectedDate); // 表示中の日付
		model.addAttribute("trainingList", trainingList); // その日のデータ
		model.addAttribute("prevMonth", yearMonth.minusMonths(1).atDay(1)); // 前月の1日
		model.addAttribute("nextMonth", yearMonth.plusMonths(1).atDay(1)); // 次月の1日
		model.addAttribute("partList", partList);
		model.addAttribute("isDailyCompleted", isDailyCompleted);

		return "menu";
	}

	@PostMapping("/menu/save")
	public String save(@ModelAttribute Training training, Principal principal) {
		// 1. ログインユーザーのIDをセット（本来はService等で取得）
		// ※今回は簡易的にログイン名をLongに変換するか、固定値を振る想定
		training.setUserId(1L);

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
		System.out.println("削除リクエストが来ました！ ID: " + id);
		trainingService.deleteTraining(id);
		return "redirect:/menu";
	}

	@GetMapping("/api/training-items")
	@ResponseBody // これを付けると、ListがそのままJSON形式でJavaScriptに渡ります
	public List<TrainingItemMaster> getItems(@RequestParam String partCode) {
		return trainingMasterDao.selectItemsByPart(partCode);
	}
}