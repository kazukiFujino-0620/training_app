package com.example.traning.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** ita3-3: Web版退会画面の一般ユーザー（即時削除）/ジム所属ユーザー（申請制）分岐を検証する。 */
@ExtendWith(MockitoExtension.class)
class WithdrawalControllerTest {

  @Mock private WithdrawalService withdrawalService;
  @Mock private UserService userService;
  @Mock private Model model;
  @Mock private Principal principal;
  @Mock private HttpServletRequest request;
  @Mock private HttpSession session;
  @Mock private RedirectAttributes redirectAttributes;

  private WithdrawalController controller;
  private User loginUser;

  @BeforeEach
  void setUp() {
    controller = new WithdrawalController(withdrawalService, userService);
    loginUser = new User();
    loginUser.setUserId(10);
    lenient().when(principal.getName()).thenReturn("user@example.com");
    lenient().when(userService.getUserByEmail("user@example.com")).thenReturn(loginUser);
  }

  @Test
  void index_一般ユーザーはhasPendingがfalse固定でisGeneralUserがtrue() {
    when(withdrawalService.isGeneralUser(loginUser)).thenReturn(true);

    String view = controller.index(model, principal);

    assertThat(view).isEqualTo("user/withdrawal");
    verify(model).addAttribute("isGeneralUser", true);
    verify(model).addAttribute("hasPending", false);
    verify(withdrawalService, never()).hasPendingRequest(anyLong());
  }

  @Test
  void index_ジム所属ユーザーはhasPendingRequestの結果を反映する() {
    when(withdrawalService.isGeneralUser(loginUser)).thenReturn(false);
    when(withdrawalService.hasPendingRequest(10L)).thenReturn(true);

    controller.index(model, principal);

    verify(model).addAttribute("isGeneralUser", false);
    verify(model).addAttribute("hasPending", true);
  }

  @Test
  void submit_一般ユーザーは即時削除してセッションを破棄しログイン画面へ遷移する() {
    when(withdrawalService.isGeneralUser(loginUser)).thenReturn(true);
    when(request.getSession(false)).thenReturn(session);

    String view = controller.submit(null, null, true, principal, request, redirectAttributes);

    verify(withdrawalService).selfDeleteImmediately(10L);
    verify(withdrawalService, never()).createRequest(anyLong(), any(), any());
    verify(session).invalidate();
    assertThat(view).isEqualTo("redirect:/login?accountDeleted");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void submit_一般ユーザーで削除失敗時は退会画面にエラーを表示する() {
    when(withdrawalService.isGeneralUser(loginUser)).thenReturn(true);
    doThrow(new IllegalStateException("既に退会申請中です"))
        .when(withdrawalService)
        .selfDeleteImmediately(10L);

    String view = controller.submit(null, null, true, principal, request, redirectAttributes);

    assertThat(view).isEqualTo("redirect:/user/withdrawal");
    verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("既に退会申請中です"));
  }

  @Test
  void submit_ジム所属ユーザーはcreateRequestを呼ぶ() {
    when(withdrawalService.isGeneralUser(loginUser)).thenReturn(false);

    String view = controller.submit("OTHER", "テスト", true, principal, request, redirectAttributes);

    verify(withdrawalService).createRequest(10L, "OTHER", "テスト");
    verify(withdrawalService, never()).selfDeleteImmediately(anyLong());
    assertThat(view).isEqualTo("redirect:/user/withdrawal");
  }

  @Test
  void submit_未チェックの場合はサービスを呼ばずエラーを表示する() {
    String view = controller.submit(null, null, false, principal, request, redirectAttributes);

    assertThat(view).isEqualTo("redirect:/user/withdrawal");
    verify(withdrawalService, times(0)).isGeneralUser(any());
    verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), eq("確認チェックボックスにチェックを入れてください"));
  }
}
