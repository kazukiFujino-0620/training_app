package com.example.traning.withdrawal;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user/withdrawal")
@Slf4j
public class WithdrawalController {

  private final WithdrawalService withdrawalService;
  private final UserService userService;

  public WithdrawalController(WithdrawalService withdrawalService, UserService userService) {
    this.withdrawalService = withdrawalService;
    this.userService = userService;
  }

  @GetMapping
  public String index(Model model, Principal principal) {
    User loginUser = userService.getUserByEmail(principal.getName());
    boolean hasPending = withdrawalService.hasPendingRequest(loginUser.getUserId().longValue());
    model.addAttribute("loginUser", loginUser);
    model.addAttribute("hasPending", hasPending);
    return "user/withdrawal";
  }

  @PostMapping
  public String submit(
      @RequestParam(required = false) String reasonType,
      @RequestParam(required = false) String reasonText,
      @RequestParam(defaultValue = "false") boolean confirmed,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    if (!confirmed) {
      redirectAttributes.addFlashAttribute("errorMessage", "確認チェックボックスにチェックを入れてください");
      return "redirect:/user/withdrawal";
    }

    User loginUser = userService.getUserByEmail(principal.getName());
    try {
      withdrawalService.createRequest(loginUser.getUserId().longValue(), reasonType, reasonText);
      redirectAttributes.addFlashAttribute("successMessage", "退会申請を受け付けました。確認メールをお送りしました。");
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/user/withdrawal";
  }

  @PostMapping("/cancel")
  public String cancel(Principal principal, RedirectAttributes redirectAttributes) {
    User loginUser = userService.getUserByEmail(principal.getName());
    try {
      withdrawalService.cancelRequest(loginUser.getUserId().longValue());
      redirectAttributes.addFlashAttribute("successMessage", "退会申請をキャンセルしました");
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/user/withdrawal";
  }
}
