package com.example.traning.user.controller;

import com.example.traning.common.WebErrorCode;
import com.example.traning.line.LineAccountLinkService;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import com.example.traning.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 通知方法（メール/LINE）の選択と、既存ユーザーへのLINEアカウント後付け連携画面（ita4-1 残作業）。
 *
 * <p>連携完了後は、通知先としてだけでなく{@link com.example.traning.user.service.CustomOAuth2UserService}
 * 側でLINEログインの照合にも使われるようになる。
 */
@Controller
@RequestMapping("/user/notifications")
@Slf4j
public class NotificationSettingsController {

  private static final String STATE_SESSION_KEY = "line_link_oauth_state";
  private static final Set<String> VALID_METHODS = Set.of("EMAIL", "LINE", "BOTH");
  private static final Set<String> LINE_REQUIRED_METHODS = Set.of("LINE", "BOTH");

  private final UserService userService;
  private final ProfileService profileService;
  private final LineAccountLinkService lineAccountLinkService;

  @Value("${line.messaging.official-account-id:}")
  private String officialAccountId;

  public NotificationSettingsController(
      UserService userService,
      ProfileService profileService,
      LineAccountLinkService lineAccountLinkService) {
    this.userService = userService;
    this.profileService = profileService;
    this.lineAccountLinkService = lineAccountLinkService;
  }

  @GetMapping
  public String show(Model model, Principal principal) {
    User user = userService.getUserByEmail(principal.getName());
    model.addAttribute("user", user);
    model.addAttribute("lineLinked", user.getLineId() != null);
    if (officialAccountId != null && !officialAccountId.isBlank()) {
      model.addAttribute("addFriendUrl", "https://line.me/R/ti/p/" + officialAccountId);
    }
    return "user/notifications";
  }

  @PostMapping("/method")
  public String updateMethod(
      @RequestParam String notificationMethod,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    if (!VALID_METHODS.contains(notificationMethod)) {
      redirectAttributes.addFlashAttribute("errorMessage", "不正な通知方法です");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
      return "redirect:/user/notifications";
    }
    User user = userService.getUserByEmail(principal.getName());
    if (LINE_REQUIRED_METHODS.contains(notificationMethod) && user.getLineId() == null) {
      redirectAttributes.addFlashAttribute("errorMessage", "LINE通知を選択するには、先にLINEアカウントと連携してください");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INVALID_STATE);
      return "redirect:/user/notifications";
    }
    profileService.updateNotificationMethod(user.getUserId(), notificationMethod);
    redirectAttributes.addFlashAttribute("successMessage", "通知方法を変更しました");
    return "redirect:/user/notifications";
  }

  @GetMapping("/line/start")
  public String startLink(HttpSession session) {
    String state = UUID.randomUUID().toString();
    session.setAttribute(STATE_SESSION_KEY, state);
    return "redirect:" + lineAccountLinkService.buildAuthorizeUrl(state);
  }

  @GetMapping("/line/callback")
  public String callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error,
      HttpSession session,
      Principal principal,
      RedirectAttributes redirectAttributes) {

    Object expectedState = session.getAttribute(STATE_SESSION_KEY);
    session.removeAttribute(STATE_SESSION_KEY);

    if (error != null) {
      redirectAttributes.addFlashAttribute("errorMessage", "LINE連携がキャンセルされました");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
      return "redirect:/user/notifications";
    }
    if (expectedState == null || !expectedState.equals(state)) {
      redirectAttributes.addFlashAttribute("errorMessage", "LINE連携の検証に失敗しました。もう一度お試しください");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
      return "redirect:/user/notifications";
    }

    User user = userService.getUserByEmail(principal.getName());
    try {
      lineAccountLinkService.completeLink(user.getUserId(), code);
      redirectAttributes.addFlashAttribute("successMessage", "LINEアカウントと連携しました");
    } catch (IllegalArgumentException e) {
      log.warn("LINE連携失敗（他ユーザーと連携済み）: {}", e.getMessage());
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INVALID_STATE);
    } catch (IllegalStateException e) {
      log.warn("LINE連携失敗（LINE API通信エラー）", e);
      redirectAttributes.addFlashAttribute("errorMessage", "LINE連携に失敗しました。時間をおいて再度お試しください");
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.INTERNAL_ERROR);
    }
    return "redirect:/user/notifications";
  }
}
