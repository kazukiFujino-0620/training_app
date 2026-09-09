package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.mobile.dto.UpdateTrainingRequest;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ita7-1: モバイルのトレーニング本体（種目名・部位・日付）更新エンドポイントを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileTrainingControllerUpdateTrainingTest {

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

  private UpdateTrainingRequest request() {
    UpdateTrainingRequest req = new UpdateTrainingRequest();
    req.setMenu("ベンチプレス");
    req.setPartCode("CHEST");
    req.setTrainingDate(LocalDate.of(2026, 1, 1));
    return req;
  }

  @Test
  void updateTraining_存在しない場合は404を返す() {
    when(trainingDao.selectById(1L)).thenReturn(null);

    ResponseEntity<Void> response = controller.updateTraining(5L, 1L, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void updateTraining_他人のトレーニングは403を返す() {
    Training training = new Training();
    training.setId(1L);
    training.setUserId(99L);
    when(trainingDao.selectById(1L)).thenReturn(training);

    ResponseEntity<Void> response = controller.updateTraining(5L, 1L, request());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void updateTraining_自分のトレーニングは204を返しDaoに委譲する() {
    Training training = new Training();
    training.setId(1L);
    training.setUserId(5L);
    when(trainingDao.selectById(1L)).thenReturn(training);

    UpdateTrainingRequest req = request();
    ResponseEntity<Void> response = controller.updateTraining(5L, 1L, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(trainingDao)
        .updateBasicInfoById(
            eq(1L), eq("ベンチプレス"), eq("CHEST"), eq(LocalDate.of(2026, 1, 1)), any());
  }
}
