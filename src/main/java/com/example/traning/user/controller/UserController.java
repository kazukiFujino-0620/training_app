package com.example.traning.user.controller;

import com.example.traning.user.form.SignupForm;
import com.example.traning.user.service.AccountRestoreRequiredException;
import com.example.traning.user.service.SignupService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

  private final SignupService signupService;
  private static final Logger log = LoggerFactory.getLogger(UserController.class);

  public UserController(SignupService signupService) {
    this.signupService = signupService;
  }

  @GetMapping("/")
  public String index() {
    return "redirect:/login";
  }

  /** ita3-3: 登録用LP（アプリ紹介・招待コード入力）。QR/URL経由での未ログインアクセスを想定。 */
  @GetMapping("/welcome")
  public String welcome(@RequestParam(required = false) String inviteCode, Model model) {
    model.addAttribute("inviteCode", inviteCode == null ? "" : inviteCode);
    return "welcome";
  }

  @GetMapping("/login")
  public String login() {
    return "auth/login";
  }

  @GetMapping("/signup")
  public String signup(@RequestParam(required = false) String inviteCode, Model model) {
    SignupForm signupForm = new SignupForm();
    // ita3-3: 登録LPのQR/URL（例: /signup?inviteCode=XXX）からの遷移時にコードを事前入力する
    if (inviteCode != null && !inviteCode.isBlank()) {
      signupForm.setInviteCode(inviteCode);
    }
    model.addAttribute("signupForm", signupForm);
    return "auth/signup";
  }

  @PostMapping("/signup")
  public String processSignup(
      @Validated @ModelAttribute SignupForm signupForm, BindingResult result, Model model) {
    if (result.hasErrors()) {
      log.error("バリデーションチェックエラーが発生しました。");
      log.error("バリデーションエラー: {}", result.getAllErrors());
      return "auth/signup";
    }
    try {
      if (!signupService.register(signupForm)) {
        model.addAttribute("errorMessage", "登録に失敗しました。入力内容をご確認ください。");
        return "auth/signup";
      }
    } catch (AccountRestoreRequiredException e) {
      return "redirect:/account/restore/sent";
    } catch (IllegalArgumentException e) {
      model.addAttribute("errorMessage", e.getMessage());
      return "auth/signup";
    } catch (Exception e) {
      log.error("予期せぬエラーが発生しました。", e);
      model.addAttribute("errorMessage", "登録中にエラーが発生しました。時間をおいて再度お試しください。");
      return "auth/signup";
    }
    String encodedEmail = URLEncoder.encode(signupForm.getEmail(), StandardCharsets.UTF_8);
    return "redirect:/login?registered&email=" + encodedEmail;
  }
}
