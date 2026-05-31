package com.example.traning.user.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.traning.user.User;
import com.example.traning.user.form.ProfileForm;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;

@Controller
@RequestMapping("/user/profile")
public class ProfileController {

    private final UserService userService;
    private final ProfileService profileService;

    public ProfileController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    @GetMapping
    public String showProfile(Model model, Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        ProfileForm form = new ProfileForm();
        form.setUserName(user.getUserName());
        form.setHeightCm(user.getHeightCm());
        form.setWeightKg(user.getWeightKg());
        form.setGender(user.getGender());
        form.setBirthDate(user.getBirthDate());
        model.addAttribute("profileForm", form);
        model.addAttribute("loginUser", user);
        model.addAttribute("user", user);
        return "user/profile";
    }

    @PostMapping
    public String updateProfile(
            @Validated @ModelAttribute("profileForm") ProfileForm form,
            BindingResult result,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            User user = userService.getUserByEmail(principal.getName());
            model.addAttribute("loginUser", user);
            model.addAttribute("user", user);
            return "user/profile";
        }
        profileService.updateProfile(principal.getName(), form);
        redirectAttributes.addFlashAttribute("successMessage", "プロフィールを更新しました");
        return "redirect:/user/profile";
    }
}
