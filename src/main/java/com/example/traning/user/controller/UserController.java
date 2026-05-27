package com.example.traning.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.example.traning.user.form.SignupForm;
import com.example.traning.user.service.AccountRestoreRequiredException;
import com.example.traning.user.service.SignupService;

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

	@GetMapping("/login")
	public String login() {
		return "auth/login";
	}

	@GetMapping("/signup")
	public String signup(Model model) {
		model.addAttribute("signupForm", new SignupForm());
		return "auth/signup";
	}

	@PostMapping("/signup")
	public String processSignup(@Validated @ModelAttribute SignupForm signupForm, BindingResult result, Model model) {
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
		} catch (Exception e) {
			log.error("予期せぬエラーが発生しました。", e);
			model.addAttribute("errorMessage", "登録中にエラーが発生しました。時間をおいて再度お試しください。");
			return "auth/signup";
		}
		return "redirect:/login";
	}
}
