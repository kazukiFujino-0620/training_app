package com.example.traning.user.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private UserService userService;

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

        return "admin/user_training_detail";
    }
}