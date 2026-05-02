package com.example.traning.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.traning.entity.User;
import com.example.traning.service.UserService;

@Controller
@RequestMapping("/admin")
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
}