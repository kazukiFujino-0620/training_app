package com.example.traning.user.controller;

import com.example.traning.user.form.TrainerSignupForm;
import com.example.traning.user.service.AccountRestoreRequiredException;
import com.example.traning.user.service.TrainerSignupService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** トレーナー用新規登録ルート（ita4-3）。一般ユーザー向け{@code /signup}とは別ルートで、招待コードによりROLE_STORE_ADMINとして登録する。 */
@Controller
@Slf4j
public class TrainerSignupController {

  private final TrainerSignupService trainerSignupService;

  public TrainerSignupController(TrainerSignupService trainerSignupService) {
    this.trainerSignupService = trainerSignupService;
  }

  @GetMapping("/signup/trainer")
  public String signup(Model model) {
    model.addAttribute("trainerSignupForm", new TrainerSignupForm());
    return "auth/signup_trainer";
  }

  @PostMapping("/signup/trainer")
  public String processSignup(
      @Validated @ModelAttribute TrainerSignupForm trainerSignupForm,
      BindingResult result,
      Model model) {
    if (result.hasErrors()) {
      return "auth/signup_trainer";
    }
    try {
      trainerSignupService.register(trainerSignupForm);
    } catch (AccountRestoreRequiredException e) {
      return "redirect:/account/restore/sent";
    } catch (IllegalArgumentException e) {
      model.addAttribute("errorMessage", e.getMessage());
      return "auth/signup_trainer";
    } catch (Exception e) {
      log.error("トレーナー登録中に予期せぬエラーが発生しました。", e);
      model.addAttribute("errorMessage", "登録中にエラーが発生しました。時間をおいて再度お試しください。");
      return "auth/signup_trainer";
    }
    String encodedEmail = URLEncoder.encode(trainerSignupForm.getEmail(), StandardCharsets.UTF_8);
    return "redirect:/login?registered&email=" + encodedEmail;
  }
}
