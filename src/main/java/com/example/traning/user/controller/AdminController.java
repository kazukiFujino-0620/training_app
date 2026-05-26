package com.example.traning.user.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
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
import com.example.traning.user.form.UserAdminUpdateForm;
import com.example.traning.user.service.UserService;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理者専用コントローラー。
 *
 * ★ 修正ポイント（指摘1・3対応）
 * クラスレベルに @PreAuthorize("hasRole('ADMIN')") を付与することで、
 * このコントローラーの全メソッドに管理者権限チェックを一括適用する。
 * SecurityConfig の URL パターン設定と合わせた多層防御。
 *
 * また、userId をパスパラメータや @RequestParam で受け取るエンドポイントでは
 * ユーザー存在チェックを行い、存在しない ID へのアクセスに対して
 * 適切なエラーを返すよう修正（IDOR の影響範囲を縮小）。
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')") // ★ クラスレベルで全メソッドに管理者権限を適用
@Slf4j
public class AdminController {

    private final UserService userService;
    private final TrainingDao trainingDao;
    private final TrainingDetailDao trainingDetailDao;

    public AdminController(UserService userService, TrainingDao trainingDao,
            TrainingDetailDao trainingDetailDao) {
        this.userService = userService;
        this.trainingDao = trainingDao;
        this.trainingDetailDao = trainingDetailDao;
    }

    /**
     * ユーザー一覧画面を表示する。
     */
    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("userList", userService.findAll());
        return "admin/user_list";
    }

    /**
     * ユーザー編集画面を表示する。
     *
     * ★ getUserById は存在しない ID の場合 RuntimeException をスローするため、
     * 存在しないユーザーIDを指定した場合は自動的に 500 → 適切なハンドラがあれば 404 になる。
     * GlobalExceptionHandler で RuntimeException → 404 へマッピングすることを推奨。
     */
    @GetMapping("/user/edit/{id}")
    public String showEditUser(@PathVariable("id") Integer id, Model model) {
        User user = userService.getUserById(id); // 存在しなければ RuntimeException
        model.addAttribute("user", user);
        return "admin/user_edit";
    }

    /**
     * ユーザー情報更新。
     * UserAdminUpdateForm を使い userName / enabled のみを更新する。
     * role / password は このエンドポイントでは変更不可（Mass Assignment 防止）。
     */
    @PostMapping("/user/update")
    public String updateUser(@Validated @ModelAttribute UserAdminUpdateForm form,
            BindingResult result,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "入力内容に誤りがあります。再度ご確認ください。");
            return "redirect:/admin/user/edit/" + form.getUserId();
        }
        User existing = userService.getUserById(form.getUserId());
        User updatedUser = existing.toBuilder()
                .userName(form.getUserName())
                .enabled(form.getEnabled())
                .updatedDatetime(LocalDateTime.now())
                .build();
        userService.updateUserInfo(updatedUser);
        return "redirect:/admin/users";
    }

    @GetMapping("/all-users-training")
    public String showAllUsersTrainingList(
            @RequestParam(name = "userName", required = false) String userName,
            Model model) {

        List<User> users;
        if (userName != null && !userName.isEmpty()) {
            users = userService.searchUsers(userName);
        } else {
            users = userService.findAll();
        }

        model.addAttribute("userList", users);
        model.addAttribute("userName", userName);
        return "admin/all_users_training_list";
    }

    /**
     * ユーザーのトレーニング詳細画面を表示する。
     *
     * ★ getUserById でユーザー存在を確認し、
     * 存在しない ID を指定された場合は RuntimeException で処理を中断する。
     */
    @GetMapping("/user/training-detail/{id}")
    public String showUserTrainingDetail(
            @PathVariable("id") Integer id,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        log.info("詳細画面表示開始: ユーザーID = {}", id);

        // ★ ユーザー存在確認（存在しなければ RuntimeException をスロー）
        User user = userService.getUserById(id);
        model.addAttribute("user", user);

        LocalDate targetMonth = (date != null)
                ? date.withDayOfMonth(1)
                : LocalDate.now().withDayOfMonth(1);

        List<LocalDate> dateList = userService.generateCalendarDates(targetMonth);
        List<String> dayStatusList = userService.getDayStatusList(id, dateList);

        model.addAttribute("targetMonth", targetMonth);
        model.addAttribute("prevMonth", targetMonth.minusMonths(1));
        model.addAttribute("nextMonth", targetMonth.plusMonths(1));
        model.addAttribute("dateList", dateList);
        model.addAttribute("status", dayStatusList);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("userId", id);

        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            List<TrainingDao.VolumeResult> volumeData = trainingDao.selectVolumeList(
                    id.longValue(), null, startDate.toString(), endDate.toString());

            model.addAttribute("volumeData", volumeData);
        } catch (Exception e) {
            log.error("グラフデータ取得エラー: ユーザーID: {}", id, e);
            model.addAttribute("volumeData", List.of());
        }

        return "admin/user_training_detail";
    }

    /**
     * 指定ユーザー・日付のトレーニング詳細を取得する API（管理者用）。
     *
     * ★ @PreAuthorize はクラスレベルで適用済み。
     * 加えて、受け取った userId でユーザー存在確認を実施し、
     * 存在しない ID を渡されても意図しないデータ操作が起きないようにする。
     */
    @GetMapping("/training-details")
    @ResponseBody
    public ResponseEntity<List<Training>> getTrainingDetails(
            @RequestParam("userId") Integer userId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("API: トレーニング詳細取得 - ユーザーID: {}, 日付: {}", userId, date);

        // ★ ユーザー存在確認
        userService.getUserById(userId); // 存在しなければ RuntimeException → 404 推奨

        try {
            List<Training> trainings = trainingDao.selectByUserIdAndDate(userId, date);
            log.info("トレーニング基本データ取得: {} 件", trainings.size());

            for (Training training : trainings) {
                try {
                    List<TrainingDetail> details = trainingDetailDao.selectByTrainingId(training.getId());
                    training.setDetails(details);
                } catch (Exception e) {
                    log.error("トレーニングID {} の詳細取得エラー", training.getId(), e);
                    training.setDetails(List.of());
                }
            }

            return ResponseEntity.ok(trainings);

        } catch (Exception e) {
            log.error("トレーニング詳細取得エラー: ユーザーID: {}, 日付: {}", userId, date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 指定ユーザーのトレーニングボリュームをグラフ用に返す API（管理者用）。
     *
     * ★ @PreAuthorize はクラスレベルで適用済み。
     * ユーザー存在確認を追加し、IDOR の影響範囲を縮小。
     */
    @GetMapping("/chart-data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getChartData(
            @RequestParam("userId") Integer userId,
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate) {

        log.info("API: グラフデータ取得 - ユーザーID: {}, 開始日: {}, 終了日: {}",
                userId, startDate, endDate);

        // ★ ユーザー存在確認
        userService.getUserById(userId);

        try {
            LocalDate end = (endDate != null) ? LocalDate.parse(endDate) : LocalDate.now();
            LocalDate start = (startDate != null) ? LocalDate.parse(startDate) : end.minusDays(30);

            Map<String, List<TrainingDao.VolumeResult>> partData = new HashMap<>();
            String[] partCodes = { "CHEST", "BACK", "ARM", "SHOULDER", "LEG" };
            for (String partCode : partCodes) {
                partData.put(partCode, trainingDao.selectVolumeList(
                        userId.longValue(), partCode, start.toString(), end.toString()));
            }

            Map<String, Object> chartData = new HashMap<>();
            List<String> labels = new ArrayList<>();
            List<Double> chest = new ArrayList<>();
            List<Double> back = new ArrayList<>();
            List<Double> arms = new ArrayList<>();
            List<Double> shoulders = new ArrayList<>();
            List<Double> legs = new ArrayList<>();

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            for (int i = 0; i < daysBetween; i++) {
                labels.add(start.plusDays(i).toString());
                chest.add(0.0);
                back.add(0.0);
                arms.add(0.0);
                shoulders.add(0.0);
                legs.add(0.0);
            }

            fillVolumeList(partData.get("CHEST"), chest, start, daysBetween);
            fillVolumeList(partData.get("BACK"), back, start, daysBetween);
            fillVolumeList(partData.get("ARM"), arms, start, daysBetween);
            fillVolumeList(partData.get("SHOULDER"), shoulders, start, daysBetween);
            fillVolumeList(partData.get("LEG"), legs, start, daysBetween);

            chartData.put("labels", labels);
            chartData.put("chest", chest);
            chartData.put("back", back);
            chartData.put("arms", arms);
            chartData.put("shoulders", shoulders);
            chartData.put("legs", legs);

            log.info("グラフデータ取得完了 (期間: {} ~ {})", start, end);
            return ResponseEntity.ok(chartData);

        } catch (Exception e) {
            log.error("グラフデータ取得エラー: ユーザーID: {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── private helper ────────────────────────────────────────────────────

    private void fillVolumeList(List<TrainingDao.VolumeResult> results,
            List<Double> target,
            LocalDate start,
            long daysBetween) {
        if (results == null)
            return;
        for (TrainingDao.VolumeResult data : results) {
            int index = (int) java.time.temporal.ChronoUnit.DAYS.between(
                    start, LocalDate.parse(data.trainingDate));
            if (index >= 0 && index < daysBetween) {
                target.set(index, target.get(index) + data.totalVolume);
            }
        }
    }
}