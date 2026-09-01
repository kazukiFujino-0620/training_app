package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** itバグ-10: モバイルのトレーニング並び替えエンドポイントを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileTrainingControllerReorderTest {

  @Mock private TrainingService trainingService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private PersonalRecordService personalRecordService;
  @Mock private UserDao userDao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private CalorieCalculator calorieCalculator;

  private MobileTrainingController controller;

  @BeforeEach
  void setUp() {
    controller =
        new MobileTrainingController(
            trainingService,
            trainingDao,
            trainingDetailDao,
            personalRecordService,
            userDao,
            trainingMasterDao,
            calorieCalculator);
  }

  @Test
  void reorder_正常な並び替えは204を返しサービスに委譲する() {
    List<Long> orderedIds = List.of(3L, 1L, 2L);
    doNothing().when(trainingService).reorderTrainings(orderedIds, 5L);

    ResponseEntity<?> response = controller.reorder(5L, orderedIds);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(trainingService).reorderTrainings(orderedIds, 5L);
  }

  @Test
  void reorder_他人のトレーニングIDが含まれる場合は403を返す() {
    List<Long> orderedIds = List.of(99L);
    doThrow(new IllegalArgumentException("このトレーニングを変更する権限がありません"))
        .when(trainingService)
        .reorderTrainings(orderedIds, 5L);

    ResponseEntity<?> response = controller.reorder(5L, orderedIds);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }
}
