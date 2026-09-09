package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.body.BodyMeasurement;
import com.example.traning.body.BodyMeasurementService;
import com.example.traning.mobile.dto.MobileBodyMeasurementResponse;
import com.example.traning.mobile.dto.SaveBodyMeasurementRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ita7-1: モバイルの体重・体脂肪率手動記録エンドポイントを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileBodyMeasurementControllerTest {

  @Mock private BodyMeasurementService bodyMeasurementService;

  private MobileBodyMeasurementController controller;

  @BeforeEach
  void setUp() {
    controller = new MobileBodyMeasurementController(bodyMeasurementService);
  }

  @Test
  void getAll_エンティティを直接返さずモバイル専用DTOに変換して返す() {
    BodyMeasurement m = new BodyMeasurement();
    m.id = 1L;
    m.userId = 5L;
    m.measuredDate = LocalDate.of(2026, 1, 1);
    m.weightKg = 70.5;
    m.bodyFatPct = 15.0;
    m.memo = "メモ";
    m.source = "MANUAL";
    when(bodyMeasurementService.getAll(5L)).thenReturn(List.of(m));

    ResponseEntity<List<MobileBodyMeasurementResponse>> response = controller.getAll(5L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    MobileBodyMeasurementResponse dto = response.getBody().get(0);
    assertThat(dto.id()).isEqualTo(1L);
    assertThat(dto.weightKg()).isEqualTo(70.5);
    assertThat(dto.source()).isEqualTo("MANUAL");
  }

  @Test
  void save_未来日は400相当のIllegalArgumentExceptionをスローする() {
    SaveBodyMeasurementRequest req = new SaveBodyMeasurementRequest();
    req.setMeasuredDate(LocalDate.now().plusDays(1));
    req.setWeightKg(70.0);

    assertThatThrownBy(() -> controller.save(5L, req)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void save_正常な日付は204を返しサービスに委譲する() {
    SaveBodyMeasurementRequest req = new SaveBodyMeasurementRequest();
    req.setMeasuredDate(LocalDate.of(2026, 1, 1));
    req.setWeightKg(70.0);
    req.setBodyFatPct(15.0);
    req.setMemo("メモ");

    ResponseEntity<Void> response = controller.save(5L, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(bodyMeasurementService).save(5L, LocalDate.of(2026, 1, 1), 70.0, 15.0, "メモ");
  }

  @Test
  void delete_204を返しサービスに委譲する() {
    ResponseEntity<Void> response = controller.delete(5L, 1L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(bodyMeasurementService).delete(1L, 5L);
  }
}
