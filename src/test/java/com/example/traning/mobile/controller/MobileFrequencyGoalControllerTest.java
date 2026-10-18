package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.example.traning.goal.FrequencyGoalService;
import com.example.traning.goal.FrequencyGoalSummary;
import com.example.traning.mobile.dto.FrequencyGoalUpdateRequest;
import com.example.traning.mobile.dto.MobileFrequencyGoalResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 機能見直し-1-#7: モバイルの週の回数目標API。 */
@ExtendWith(MockitoExtension.class)
class MobileFrequencyGoalControllerTest {

  @Mock private FrequencyGoalService frequencyGoalService;

  private MobileFrequencyGoalController controller;
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @BeforeEach
  void setUp() {
    controller = new MobileFrequencyGoalController(frequencyGoalService);
  }

  @Test
  void get_JWTのユーザー本人の表示データを文言つきで返す() {
    when(frequencyGoalService.getSummary(eq(5L), any(LocalDate.class)))
        .thenReturn(new FrequencyGoalSummary(true, 3, 2, false, 4, 3, 3, false));

    ResponseEntity<MobileFrequencyGoalResponse> res = controller.get(5L);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    MobileFrequencyGoalResponse body = res.getBody();
    assertThat(body.goalLabel()).isEqualTo("週3回");
    assertThat(body.thisWeekLabel()).isEqualTo("今週 2/3回");
    assertThat(body.resultLabel()).isEqualTo("直近4週間 3/4週達成");
    assertThat(body.percentLabel()).isEqualTo("（75%）");
    assertThat(body.achievementPct()).isEqualTo(75);
    assertThat(body.programSheetLabel()).isNull();
  }

  @Test
  void get_未設定なら案内用に文言を返さない() {
    when(frequencyGoalService.getSummary(eq(5L), any(LocalDate.class)))
        .thenReturn(new FrequencyGoalSummary(false, null, 0, false, 0, 0, null, false));

    MobileFrequencyGoalResponse body = controller.get(5L).getBody();

    assertThat(body.goalSet()).isFalse();
    assertThat(body.goalLabel()).isNull();
    assertThat(body.resultLabel()).isNull();
    assertThat(body.notes()).isEmpty();
  }

  @Test
  void update_本人の目標を保存してから保存後の表示データを返す() {
    FrequencyGoalUpdateRequest req = new FrequencyGoalUpdateRequest();
    req.setWeeklyTargetCount(4);
    when(frequencyGoalService.getSummary(eq(5L), any(LocalDate.class)))
        .thenReturn(new FrequencyGoalSummary(true, 3, 0, false, 0, 0, 4, false));

    ResponseEntity<MobileFrequencyGoalResponse> res = controller.update(5L, req);

    InOrder order = inOrder(frequencyGoalService);
    order.verify(frequencyGoalService).updateGoal(eq(5L), eq(4), any(LocalDate.class));
    order.verify(frequencyGoalService).getSummary(eq(5L), any(LocalDate.class));
    assertThat(res.getBody().manualTarget()).isEqualTo(4);
  }

  @Test
  void update_リクエストは1から7回の範囲だけを受け付ける() {
    assertThat(violations(null)).isEqualTo(1);
    assertThat(violations(0)).isEqualTo(1);
    assertThat(violations(1)).isZero();
    assertThat(violations(7)).isZero();
    assertThat(violations(8)).isEqualTo(1);
  }

  private int violations(Integer count) {
    FrequencyGoalUpdateRequest req = new FrequencyGoalUpdateRequest();
    req.setWeeklyTargetCount(count);
    return validator.validate(req).size();
  }
}
