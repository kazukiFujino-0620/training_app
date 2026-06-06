package com.example.traning.emailchange;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.traning.dao.UserDao;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/user/email")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class EmailChangeController {

    private final EmailChangeService emailChangeService;
    private final TrainingService trainingService;
    private final UserDao userDao;

    @GetMapping
    public String showForm(
            @RequestParam(required = false) String sent,
            @RequestParam(required = false) String changed,
            Model model, Principal principal) {
        User user = trainingService.getUserByEmail(principal.getName());
        boolean isOAuthUser = (user.getPassword() == null);
        model.addAttribute("isOAuthUser", isOAuthUser);
        model.addAttribute("currentEmail", user.getEmail());
        if ("true".equals(sent)) {
            model.addAttribute("infoMessage", "確認メールを送信しました。新しいメールアドレスのリンクをクリックしてください。");
        }
        if ("true".equals(changed)) {
            model.addAttribute("successMessage", "メールアドレスを変更しました。");
        }
        return "user/email_change";
    }

    @PostMapping
    public String initiateChange(
            @RequestParam String newEmail,
            @RequestParam String currentPassword,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        try {
            User user = trainingService.getUserByEmail(principal.getName());
            emailChangeService.initiateChange(user.getUserId().longValue(), user.getEmail(), newEmail, currentPassword);
            return "redirect:/user/email?sent=true";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/user/email";
        }
    }

    @GetMapping("/confirm")
    public String confirmChange(
            @RequestParam String token,
            RedirectAttributes redirectAttributes) {
        try {
            emailChangeService.confirmChange(token);
            return "redirect:/user/email?changed=true";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/user/email";
        }
    }
}
