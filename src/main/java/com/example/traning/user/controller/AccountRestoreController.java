package com.example.traning.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.traning.user.service.AccountRestoreService;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/account")
@Slf4j
public class AccountRestoreController {

    private final AccountRestoreService accountRestoreService;

    public AccountRestoreController(AccountRestoreService accountRestoreService) {
        this.accountRestoreService = accountRestoreService;
    }

    /** 復元メール送信完了の案内ページ */
    @GetMapping("/restore/sent")
    public String showSentPage() {
        return "auth/account-restore-sent";
    }

    /** トークンを受け取り復元確認ページを表示する */
    @GetMapping("/restore")
    public String showRestorePage(@RequestParam("token") String token, Model model) {
        model.addAttribute("token", token);
        return "auth/account-restore";
    }

    /** 復元を実行する */
    @PostMapping("/restore/confirm")
    public String confirmRestore(@RequestParam("token") String token, Model model) {
        try {
            accountRestoreService.restoreAccount(token);
            return "redirect:/login?restoreSuccess";
        } catch (Exception e) {
            log.warn("アカウント復元失敗: {}", e.getMessage());
            model.addAttribute("errorMessage", "無効または期限切れのURLです。再度ご登録いただくか、管理者へお問い合わせください。");
            model.addAttribute("token", token);
            return "auth/account-restore";
        }
    }
}
