package com.example.traning.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.goal.FrequencyGoalApiController.FrequencyGoalWebResponse;
import com.example.traning.goal.FrequencyGoalApiController.GoalForm;
import com.example.traning.training.service.TrainingService;
import java.security.Principal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

/** 機能見直し-1-#7: Web版の週の回数目標API（ログイン中のユーザー本人のデータのみを扱う）。 */
@ExtendWith(MockitoExtension.class)
class FrequencyGoalApiControllerTest {

  @Mock private FrequencyGoalService frequencyGoalService;
  @Mock private TrainingService trainingService;

  private FrequencyGoalApiController controller;
  private final Principal principal = () -> "me@example.com";

  @BeforeEach
  void setUp() {
    controller = new FrequencyGoalApiController(frequencyGoalService, trainingService);
  }

  @Test
  void get_ログインユーザーのIDで表示データを取得する() {
    when(trainingService.getUserIdByEmail("me@example.com")).thenReturn(5L);
    when(frequencyGoalService.getSummary(eq(5L), any(LocalDate.class)))
        .thenReturn(new FrequencyGoalSummary(true, 4, 2, true, 2, 1, null, true));

    ResponseEntity<FrequencyGoalWebResponse> res = controller.get(principal);

    FrequencyGoalWebResponse body = res.getBody();
    assertThat(body.goalLabel()).isEqualTo("週4回");
    assertThat(body.resultLabel()).isEqualTo("直近2週 1/2週達成");
    assertThat(body.percentLabel()).isNull();
    assertThat(body.notes()).containsExactly("4週たつと達成率（%）も表示します", "プログラムの予定日数を目標にしています");
    assertThat(body.programSheetLabel()).contains("今週は4回");
  }

  @Test
  void update_入力エラーは400で保存しない() {
    GoalForm form = new GoalForm();
    form.setWeeklyTargetCount(9);
    BindingResult result = new BeanPropertyBindingResult(form, "form");
    result.rejectValue("weeklyTargetCount", "Max", "週の回数目標は1〜7回の範囲で指定してください");

    ResponseEntity<?> res = controller.update(form, result, principal);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verify(frequencyGoalService, never()).updateGoal(anyLong(), anyInt(), any());
  }

  @Test
  void update_ログインユーザー本人の目標を保存する() {
    when(trainingService.getUserIdByEmail("me@example.com")).thenReturn(5L);
    when(frequencyGoalService.getSummary(eq(5L), any(LocalDate.class)))
        .thenReturn(new FrequencyGoalSummary(true, 3, 0, false, 0, 0, 3, false));
    GoalForm form = new GoalForm();
    form.setWeeklyTargetCount(3);

    ResponseEntity<?> res =
        controller.update(form, new BeanPropertyBindingResult(form, "form"), principal);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(frequencyGoalService).updateGoal(eq(5L), eq(3), any(LocalDate.class));
  }
}
