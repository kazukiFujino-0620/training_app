package com.example.traning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/** 機能見直し-1-#20: 目標の提案・返事のWeb画面の受け付け（提案できる人の判定はサービス側のテストで検証）。 */
@ExtendWith(MockitoExtension.class)
class GoalProposalControllerTest {

  @Mock private GoalProposalService goalProposalService;
  @Mock private UserService userService;
  @InjectMocks private GoalProposalController controller;

  private final Principal principal = () -> "u@example.com";
  private final User trainer = User.builder().userId(100).role("ROLE_TRAINER").build();
  private final User member = User.builder().userId(1).role("ROLE_USER").build();

  @Test
  void 提案は送ったあと会員詳細の目標のカードに戻る() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(trainer);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view =
        controller.propose(1L, 3, "BULKING", "ひとこと", LocalDate.of(2026, 12, 22), principal, attrs);

    assertThat(view).isEqualTo("redirect:/admin/user/training-detail/1#goal-card");
    verify(goalProposalService)
        .propose(
            eq(trainer),
            eq(1L),
            eq(3),
            eq("BULKING"),
            eq("ひとこと"),
            eq(LocalDate.of(2026, 12, 22)),
            any(),
            any());
    assertThat(attrs.getFlashAttributes().get("goalSuccessMessage")).isEqualTo("目標の提案を送りました。");
  }

  @Test
  void 入力の誤りは会員詳細にエラーを出す() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(trainer);
    doThrow(new IllegalArgumentException("次の見直し日は明日以降の日付を選んでください"))
        .when(goalProposalService)
        .propose(any(), any(), any(), any(), any(), any(), any(), any());
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    controller.propose(1L, 3, "BULKING", null, LocalDate.of(2026, 11, 1), principal, attrs);

    assertThat(attrs.getFlashAttributes().get("goalErrorMessage"))
        .isEqualTo("次の見直し日は明日以降の日付を選んでください");
  }

  @Test
  void 担当外や範囲外の会員への提案は403をそのまま返す() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(trainer);
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(goalProposalService)
        .propose(any(), any(), any(), any(), any(), any(), any(), any());

    assertThatThrownBy(
            () ->
                controller.propose(
                    9L,
                    3,
                    "BULKING",
                    null,
                    LocalDate.of(2026, 12, 22),
                    principal,
                    new RedirectAttributesModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void 会員はログイン中の本人として返事をしトップに戻る() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(member);
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.respond(9L, "ACCEPT", principal, attrs);

    assertThat(view).isEqualTo("redirect:/menu");
    verify(goalProposalService).respond(eq(1L), eq(9L), eq(true), any(), any());
  }

  @Test
  void 見送りも本人として返事をする() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(member);

    controller.respond(9L, "DECLINE", principal, new RedirectAttributesModelMap());

    verify(goalProposalService).respond(eq(1L), eq(9L), eq(false), any(), any());
  }

  @Test
  void 他の会員宛ての提案への返事は404をそのまま返す() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(member);
    doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
        .when(goalProposalService)
        .respond(eq(1L), eq(9L), anyBoolean(), any(), any());

    assertThatThrownBy(
            () -> controller.respond(9L, "ACCEPT", principal, new RedirectAttributesModelMap()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void 取り下げ済みの提案への返事はトップにエラーを出す() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(member);
    doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "この提案は取り下げられました。新しい提案を確認してください"))
        .when(goalProposalService)
        .respond(eq(1L), eq(9L), anyBoolean(), any(), any());
    RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();

    String view = controller.respond(9L, "ACCEPT", principal, attrs);

    assertThat(view).isEqualTo("redirect:/menu");
    assertThat(attrs.getFlashAttributes().get("error")).isEqualTo("この提案は取り下げられました。新しい提案を確認してください");
  }

  @Test
  void 返事の選択肢が不正なら400で処理しない() {
    when(userService.getUserByEmail("u@example.com")).thenReturn(member);

    assertThatThrownBy(
            () -> controller.respond(9L, "MAYBE", principal, new RedirectAttributesModelMap()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(goalProposalService, never()).respond(anyLong(), anyLong(), anyBoolean(), any(), any());
  }
}
