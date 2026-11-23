package com.example.traning.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.follow.MemberFollowService.Sort;
import com.example.traning.follow.MemberFollowService.Tab;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** 機能見直し-1-#12: 担当会員の一覧の画面の受け付け（範囲の判定はサービス側のテストで検証）。 */
@ExtendWith(MockitoExtension.class)
class MemberFollowControllerTest {

  @Mock private MemberFollowService memberFollowService;
  @Mock private UserService userService;

  private MemberFollowController controller;
  private final Principal principal = () -> "staff@example.com";
  private final User viewer = User.builder().userId(100).role("ROLE_STORE_ADMIN").build();

  @BeforeEach
  void setUp() {
    controller = new MemberFollowController(memberFollowService, userService);
  }

  @Test
  void タブと並べ替えは小文字の指定を受け付け不正な値は400() {
    when(userService.getUserByEmail("staff@example.com")).thenReturn(viewer);

    String viewName =
        controller.index("store", "store:11", "trainer", new ExtendedModelMap(), principal);

    assertThat(viewName).isEqualTo("trainer/members");
    verify(memberFollowService)
        .list(eq(viewer), eq(Tab.STORE), eq("store:11"), eq(Sort.TRAINER), any());
    assertThatThrownBy(() -> controller.index("all", null, null, new ExtendedModelMap(), principal))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void 範囲外の会員の対応済みは403をそのまま返す() {
    when(userService.getUserByEmail("staff@example.com")).thenReturn(viewer);
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(memberFollowService)
        .acknowledge(eq(viewer), eq(9L), any(), any());

    assertThatThrownBy(
            () ->
                controller.acknowledge(
                    9L, "mine", null, null, principal, new RedirectAttributesModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void 対応済みのあとは同じタブと店舗と並べ替えの一覧に戻る() {
    when(userService.getUserByEmail("staff@example.com")).thenReturn(viewer);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String redirect = controller.acknowledge(1L, "store", "gym:10", "last", principal, attrs);

    assertThat(redirect).isEqualTo("redirect:/trainer/members?tab=store&scope=gym:10&sort=last");
    assertThat(attrs.getFlashAttributes().get("successMessage")).isEqualTo("対応済みにしました。");
  }

  @Test
  void 印が無い会員の対応済みはエラーの案内を出し戻り先の店舗の指定が不正なら引き継がない() {
    when(userService.getUserByEmail("staff@example.com")).thenReturn(viewer);
    doThrow(new IllegalArgumentException("この会員にはフォロー推奨の印が付いていません"))
        .when(memberFollowService)
        .acknowledge(eq(viewer), eq(1L), any(), any());
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String redirect =
        controller.acknowledge(1L, "store", "https://evil.example", null, principal, attrs);

    assertThat(redirect).isEqualTo("redirect:/trainer/members?tab=store");
    assertThat(attrs.getFlashAttributes().get("errorMessage")).isEqualTo("この会員にはフォロー推奨の印が付いていません");
    verify(memberFollowService, never()).list(any(), any(), any(), any(), any());
  }
}
