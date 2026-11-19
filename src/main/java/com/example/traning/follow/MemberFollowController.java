package com.example.traning.follow;

import com.example.traning.audit.AuditLog;
import com.example.traning.common.WebErrorCode;
import com.example.traning.follow.MemberFollowService.ListView;
import com.example.traning.follow.MemberFollowService.Sort;
import com.example.traning.follow.MemberFollowService.Tab;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 担当会員の一覧（機能見直し-1-#12）。トレーナー・店舗管理者・ジム管理者・システム管理者。
 *
 * <p>どの会員を出すか・「対応済みにする」を押せるかは {@link MemberFollowService}で判定する（範囲外は403）。
 */
@Controller
@RequestMapping("/trainer/members")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN', 'TRAINER')")
public class MemberFollowController {

  private final MemberFollowService memberFollowService;
  private final UserService userService;

  public MemberFollowController(MemberFollowService memberFollowService, UserService userService) {
    this.memberFollowService = memberFollowService;
    this.userService = userService;
  }

  @GetMapping
  public String index(
      @RequestParam(required = false) String tab,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String sort,
      Model model,
      Principal principal) {
    User viewer = userService.getUserByEmail(principal.getName());
    ListView view =
        memberFollowService.list(
            viewer, parse(Tab.class, tab), scope, parse(Sort.class, sort), LocalDate.now());
    model.addAttribute("view", view);
    return "trainer/members";
  }

  @AuditLog(action = "MEMBER_FOLLOW_ACKNOWLEDGE", targetTable = "member_follow_acknowledgements")
  @PostMapping("/{memberId}/acknowledge")
  public String acknowledge(
      @PathVariable Long memberId,
      @RequestParam(required = false) String tab,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String sort,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User viewer = userService.getUserByEmail(principal.getName());
    try {
      memberFollowService.acknowledge(viewer, memberId, LocalDate.now(), LocalDateTime.now());
      redirectAttributes.addFlashAttribute("successMessage", "対応済みにしました。");
    } catch (IllegalArgumentException e) {
      redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
      redirectAttributes.addFlashAttribute("errorCode", WebErrorCode.VALIDATION_ERROR);
    }
    UriComponentsBuilder back = UriComponentsBuilder.fromPath("/trainer/members");
    Tab t = parse(Tab.class, tab);
    Sort s = parse(Sort.class, sort);
    if (t != null) back.queryParam("tab", t.name().toLowerCase(Locale.ROOT));
    if (scope != null && scope.matches("(store|gym):\\d+")) back.queryParam("scope", scope);
    if (s != null) back.queryParam("sort", s.name().toLowerCase(Locale.ROOT));
    return "redirect:" + back.build().toUriString();
  }

  /** 画面のクエリ（小文字）をenumに変換する。未指定はnull、不正な値は400。 */
  static <E extends Enum<E>> E parse(Class<E> type, String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定が不正です");
    }
  }
}
