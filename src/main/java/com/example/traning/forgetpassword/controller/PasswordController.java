package com.example.traning.forgetpassword.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.traning.forgetpassword.Service.PasswordResetService;
import com.example.traning.forgetpassword.form.PasswordForgetForm;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/password")
@Slf4j
public class PasswordController {

    @Autowired
    private PasswordResetService passwordResetService;

    // 入力画面を表示
    @GetMapping("/forget")
    public String showForgetPage() {
        return "password/forget";
    }

    // 送信ボタン押下時の処理
    @PostMapping("/forget")
    public String processForget(@Validated @ModelAttribute PasswordForgetForm form,
            BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "password/forget";
        }

        try {
            // Serviceを呼び出してトークン作成
            passwordResetService.createResetToken(form.getEmail());
        } catch (Exception e) {
            log.error("トークン作成中にエラーが発生しました", e);
            model.addAttribute("errorMessage", "ご入力いただいたメールアドレスには送信できませんでした。再度お試しいただくか、管理者へお問い合わせください。");
            return "auth/forget_password";
        }

        return "redirect:/password/sent";
    }

    // 完了画面を表示
    @GetMapping("/sent")
    public String showSentPage() {
        return "password/sent";
    }

    @GetMapping("/reset")
    public String showResetPage(@RequestParam("token") String token, Model model) {
        // URLパラメータの「token」を受け取って、HTMLに渡す
        model.addAttribute("token", token);

        // auth/reset_password.html を表示
        return "auth/reset_password";
    }

    // 新しいパスワードを送信した時の処理
    @PostMapping("/reset")
    public String processReset(@RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("passwordConfirm") String passwordConfirm,
            Model model) {

        if (!password.equals(passwordConfirm)) {
            model.addAttribute("errorMessage", "パスワードが一致しません");
            model.addAttribute("token", token);
            return "auth/reset_password";
        }

        try {
            // ServiceでDBのパスワードを更新する処理を呼ぶ
            passwordResetService.updatePassword(token, password);
            return "redirect:/login?resetSuccess"; // 成功したらログイン画面へ
        } catch (Exception e) {
            model.addAttribute("errorMessage", "無効なトークンか、期限が切れています。");
            return "auth/reset_password";
        }
    }
}
