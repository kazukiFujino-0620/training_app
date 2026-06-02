package com.example.traning.user.controller;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import com.example.traning.withdrawal.WithdrawalService;

import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin/withdrawal")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;
    private final UserService userService;

    public AdminWithdrawalController(WithdrawalService withdrawalService, UserService userService) {
        this.withdrawalService = withdrawalService;
        this.userService = userService;
    }

    @GetMapping
    public String index(Model model, Principal principal) {
        User loginUser = userService.getUserByEmail(principal.getName());
        model.addAttribute("loginUser", loginUser);
        model.addAttribute("requests", withdrawalService.findAllPendingWithUser());
        return "admin/withdrawal";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User adminUser = userService.getUserByEmail(principal.getName());
        try {
            withdrawalService.approveRequest(id, adminUser.getUserId().longValue());
            redirectAttributes.addFlashAttribute("successMessage", "退会申請を承認し、データを削除しました");
        } catch (Exception e) {
            log.error("Withdrawal approval failed - requestId: {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "処理に失敗しました: " + e.getMessage());
        }
        return "redirect:/admin/withdrawal";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User adminUser = userService.getUserByEmail(principal.getName());
        try {
            withdrawalService.rejectRequest(id, adminUser.getUserId().longValue());
            redirectAttributes.addFlashAttribute("successMessage", "退会申請を拒否しました");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "処理に失敗しました: " + e.getMessage());
        }
        return "redirect:/admin/withdrawal";
    }
}
