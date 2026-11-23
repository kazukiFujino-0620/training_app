package com.example.traning.mobile.controller;

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

import com.example.traning.goal.GoalProposalService;
import com.example.traning.goal.GoalProposalService.AgreementView;
import com.example.traning.goal.GoalProposalService.MemberProposalView;
import com.example.traning.mobile.controller.MobileGoalProposalController.AgreementResponse;
import com.example.traning.mobile.controller.MobileGoalProposalController.PendingResponse;
import com.example.traning.mobile.controller.MobileGoalProposalController.RespondRequest;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#20: モバイルの目標の提案API（JWTのユーザー本人のデータのみ）。 */
@ExtendWith(MockitoExtension.class)
class MobileGoalProposalControllerTest {

  @Mock private GoalProposalService goalProposalService;

  private MobileGoalProposalController controller;

  @BeforeEach
  void setUp() {
    controller = new MobileGoalProposalController(goalProposalService);
  }

  @Test
  void pending_本人宛ての返事待ちの提案を文言つきで返す() {
    when(goalProposalService.pendingForMember(5L))
        .thenReturn(
            Optional.of(
                new MemberProposalView(
                    9L,
                    "山本 健",
                    "週2回",
                    "週3回",
                    "維持",
                    "筋肥大",
                    "ひとこと",
                    LocalDate.of(2026, 12, 22),
                    "12/22")));

    PendingResponse body = controller.pending(5L).getBody();

    assertThat(body.proposal().id()).isEqualTo(9L);
    assertThat(body.proposal().title()).isEqualTo("山本 健トレーナーから目標の提案があります");
    assertThat(body.proposal().weeklyChanged()).isTrue();
    assertThat(body.proposal().goalModeChanged()).isTrue();
    assertThat(body.proposal().nextReviewLabel()).isEqualTo("12/22");
  }

  @Test
  void pending_提案が無ければproposalはnull() {
    when(goalProposalService.pendingForMember(5L)).thenReturn(Optional.empty());

    assertThat(controller.pending(5L).getBody().proposal()).isNull();
  }

  @Test
  void agreement_担当トレーナーと決めた目標の表示() {
    when(goalProposalService.agreementFor(5L))
        .thenReturn(
            Optional.of(
                new AgreementView(
                    "担当トレーナーと決めた目標（11/24 山本 健）", LocalDate.of(2026, 12, 22), "12/22")));

    AgreementResponse body = controller.agreement(5L).getBody();

    assertThat(body.agreed()).isTrue();
    assertThat(body.tagLabel()).isEqualTo("担当トレーナーと決めた目標（11/24 山本 健）");
    when(goalProposalService.agreementFor(6L)).thenReturn(Optional.empty());
    assertThat(controller.agreement(6L).getBody().agreed()).isFalse();
  }

  @Test
  void respond_本人として承認と見送りを受け付ける() {
    assertThat(controller.respond(5L, 9L, new RespondRequest("ACCEPT")).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    verify(goalProposalService).respond(eq(5L), eq(9L), eq(true), any(), any());

    controller.respond(5L, 10L, new RespondRequest("DECLINE"));
    verify(goalProposalService).respond(eq(5L), eq(10L), eq(false), any(), any());
  }

  @Test
  void respond_選択肢が不正なら400で処理しない() {
    assertThatThrownBy(() -> controller.respond(5L, 9L, new RespondRequest("MAYBE")))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(goalProposalService, never()).respond(anyLong(), anyLong(), anyBoolean(), any(), any());
  }

  @Test
  void respond_他の会員宛てや取り下げ済みはステータスどおりのJSONで返す() {
    doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "提案が見つかりません"))
        .when(goalProposalService)
        .respond(eq(5L), eq(99L), anyBoolean(), any(), any());

    ResponseStatusException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            ResponseStatusException.class,
            () -> controller.respond(5L, 99L, new RespondRequest("ACCEPT")));
    ResponseEntity<Map<String, String>> res = controller.handleResponseStatus(ex);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(res.getBody()).containsEntry("error", "提案が見つかりません");
  }
}
