package com.example.traning.user.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    private final UserService userService;
    private final TrainingDao trainingDao;
    private final TrainingDetailDao trainingDetailDao;

    public AdminController(UserService userService, TrainingDao trainingDao, TrainingDetailDao trainingDetailDao) {
        this.userService = userService;
        this.trainingDao = trainingDao;
        this.trainingDetailDao = trainingDetailDao;
    }

    /**
     * ユーザー一覧画面を表示する
     */
    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("userList", userService.findAll());
        return "admin/user_list";
    }

    @GetMapping("/user/edit/{id}")
    public String showEditUser(@PathVariable("id") Integer id, Model model) {
        // 1. サービスを呼び出してユーザー情報を取得
        User user = userService.getUserById(id);

        // 2. 取得した情報をModelに登録して、HTMLに渡す
        model.addAttribute("user", user);

        // 3. 編集用のHTML（admin/user_edit.html）を表示
        return "admin/user_edit";
    }

    @PostMapping("/user/update") // formのactionと合わせる
    public String updateUser(@ModelAttribute User user) {
        User updatedUser = user.toBuilder()
                .updatedDatetime(LocalDateTime.now())
                .build();

        userService.updateUserInfo(updatedUser);

        // 更新が終わったら、ユーザー一覧画面にリダイレクト（再表示）させる
        return "redirect:/admin/users";
    }

    @GetMapping("/all-users-training")
    public String showAllUsersTrainingList(
            @RequestParam(name = "userName", required = false) String userName,
            Model model) {
        model.addAttribute("userList", userService.findAll());
        List<User> users;
        if (userName != null && !userName.isEmpty()) {
            // 検索ワードがある場合は絞り込み（今後Serviceに作る）
            users = userService.searchUsers(userName);
        } else {
            // ない場合は全件表示
            users = userService.findAll();
        }

        model.addAttribute("userList", users);
        model.addAttribute("userName", userName); // 検索窓に値を残すために渡す
        return "admin/all_users_training_list";
    }

    @GetMapping("/user/training-detail/{id}")
    public String showUserTrainingDetail(
            @PathVariable("id") Integer id,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        log.info("詳細画面表示開始: ユーザーID = {}", id);
        // 1. ユーザー情報の取得
        User user = userService.getUserById(id);
        model.addAttribute("user", user);

        // 2. 表示対象の月を決定（パラメータがなければ今日）
        LocalDate targetMonth = (date != null) ? date.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1);

        // 3. カレンダー描画用のデータを取得・作成（既存のロジックを流用）
        // ※Service側に「特定月のLocalDateリストを返すメソッド」などがあるとスッキリします
        List<LocalDate> dateList = userService.generateCalendarDates(targetMonth);
        List<String> dayStatusList = userService.getDayStatusList(id, dateList);

        // 4. Modelへ詰め込み
        model.addAttribute("targetMonth", targetMonth);
        model.addAttribute("prevMonth", targetMonth.minusMonths(1));
        model.addAttribute("nextMonth", targetMonth.plusMonths(1));
        model.addAttribute("dateList", dateList);
        model.addAttribute("status", dayStatusList);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("userId", id); // HTML内のリンクで使うために必要

        // グラフデータを準備
        try {
            // 過去30日間のデータを取得
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            List<TrainingDao.VolumeResult> volumeData = trainingDao.selectVolumeList(
                    id.longValue(), null, startDate.toString(), endDate.toString());

            // グラフ用のデータをモデルに追加
            model.addAttribute("volumeData", volumeData);

        } catch (Exception e) {
            log.error("グラフデータ取得エラー: ユーザーID: {}", id, e);
            model.addAttribute("volumeData", List.of());
        }

        return "admin/user_training_detail";
    }

    /**
     * API endpoint for training details
     */
    @GetMapping("/training-details")
    @ResponseBody
    public List<Training> getTrainingDetails(
            @RequestParam("userId") Integer userId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("API: トレーニング詳細取得 - ユーザーID: {}, 日付: {}", userId, date);

        try {
            log.info("API: トレーニング詳細取得開始 - ユーザーID: {}, 日付: {}", userId, date);

            // 指定されたユーザーと日付のトレーニングデータを取得
            List<Training> trainings = trainingDao.selectByUserIdAndDate(userId, date);
            log.info("トレーニング基本データ取得: {} 件", trainings.size());

            // 詳細データを取得して設定
            for (Training training : trainings) {
                try {
                    List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(training.getId());
                    log.debug("トレーニングID {} の詳細データ: {} 件", training.getId(), details.size());
                    training.setDetails(details);
                } catch (Exception e) {
                    log.error("トレーニングID {} の詳細取得エラー", training.getId(), e);
                    training.setDetails(List.of());
                }
            }

            log.info("トレーニングデータ取得完了: {} 件", trainings.size());
            return trainings;

        } catch (Exception e) {
            log.error("トレーニング詳細取得エラー: ユーザーID: {}, 日付: {}", userId, date, e);
            return List.of(); // エラー時は空リストを返す
        }
    }

    /**
     * API endpoint for chart data
     */
    @GetMapping("/chart-data")
    @ResponseBody
    public Map<String, Object> getChartData(
            @RequestParam("userId") Integer userId,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate) {

        log.info("API: グラフデータ取得 - ユーザーID: {}, 開始日: {}, 終了日: {}", userId, startDate, endDate);

        try {
            // 日付範囲の設定（指定がない場合は過去30日間）
            LocalDate end = (endDate != null) ? LocalDate.parse(endDate) : LocalDate.now();
            LocalDate start = (startDate != null) ? LocalDate.parse(startDate) : end.minusDays(30);

            // 部位別にデータを取得
            Map<String, List<TrainingDao.VolumeResult>> partData = new HashMap<>();

            // 各部位のデータを取得
            String[] partCodes = { "CHEST", "BACK", "ARM", "SHOULDER", "LEG" };
            for (String partCode : partCodes) {
                List<TrainingDao.VolumeResult> partVolumeData = trainingDao.selectVolumeList(
                        userId.longValue(), partCode, start.toString(), end.toString());
                partData.put(partCode, partVolumeData);
            }

            // グラフ用のデータ形式に変換
            Map<String, Object> chartData = new HashMap<>();

            // 日付ラベル
            List<String> labels = new ArrayList<>();
            List<Double> chest = new ArrayList<>();
            List<Double> back = new ArrayList<>();
            List<Double> arms = new ArrayList<>();
            List<Double> shoulders = new ArrayList<>();
            List<Double> legs = new ArrayList<>();

            // 期間の日数分のデータを初期化
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            for (int i = 0; i < daysBetween; i++) {
                labels.add(start.plusDays(i).toString());
                chest.add(0.0);
                back.add(0.0);
                arms.add(0.0);
                shoulders.add(0.0);
                legs.add(0.0);
            }

            // 実際のデータを設定
            for (TrainingDao.VolumeResult data : partData.get("CHEST")) {
                int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
                if (index >= 0 && index < daysBetween) {
                    chest.set(index, chest.get(index) + data.totalVolume);
                }
            }

            for (TrainingDao.VolumeResult data : partData.get("BACK")) {
                int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
                if (index >= 0 && index < daysBetween) {
                    back.set(index, back.get(index) + data.totalVolume);
                }
            }

            for (TrainingDao.VolumeResult data : partData.get("ARM")) {
                int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
                if (index >= 0 && index < daysBetween) {
                    arms.set(index, arms.get(index) + data.totalVolume);
                }
            }

            for (TrainingDao.VolumeResult data : partData.get("SHOULDER")) {
                int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
                if (index >= 0 && index < daysBetween) {
                    shoulders.set(index, shoulders.get(index) + data.totalVolume);
                }
            }

            for (TrainingDao.VolumeResult data : partData.get("LEG")) {
                int index = (int) java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.parse(data.trainingDate));
                if (index >= 0 && index < daysBetween) {
                    legs.set(index, legs.get(index) + data.totalVolume);
                }
            }

            chartData.put("labels", labels);
            chartData.put("chest", chest);
            chartData.put("back", back);
            chartData.put("arms", arms);
            chartData.put("shoulders", shoulders);
            chartData.put("legs", legs);

            int totalDataCount = 0;
            for (List<TrainingDao.VolumeResult> partVolumeData : partData.values()) {
                totalDataCount += partVolumeData.size();
            }
            log.info("グラフデータ取得完了: {} 件 (期間: {} ~ {})", totalDataCount, start, end);
            return chartData;

        } catch (Exception e) {
            log.error("グラフデータ取得エラー: ユーザーID: {}", userId, e);
            return Map.of(); // エラー時は空マップを返す
        }
    }
}