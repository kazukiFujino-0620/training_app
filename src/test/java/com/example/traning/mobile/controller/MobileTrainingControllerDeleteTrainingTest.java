package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.restpreference.RestIntervalCalculationService;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 機能見直し-1-#1 バグ修正: スーパーセット相方削除時のグルーピング解除漏れを検証する。
 *
 * <p>MobileTrainingController.deleteTraining() は TrainingService を経由せず自前で
 * ソフトデリートを行っているため、修正はこのメソッド自体に対して行った（TrainingService.deleteTraining()は
 * どのコントローラーからも呼ばれていないデッドコードのため対象外。project-leader確認済み）。
 */
@ExtendWith(MockitoExtension.class)
class MobileTrainingControllerDeleteTrainingTest {

  private static final Long USER_ID = 1L;

  @Mock private TrainingService trainingService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private PersonalRecordService personalRecordService;
  @Mock private UserDao userDao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private CalorieCalculator calorieCalculator;
  @Mock private RestIntervalCalculationService restIntervalCalculationService;

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
            calorieCalculator,
            restIntervalCalculationService);
  }

  @Test
  void スーパーセットに属する種目を削除すると相方のグルーピングも解除される() {
    Training target = new Training();
    target.setId(10L);
    target.setUserId(USER_ID);
    target.setSupersetGroupId(999L);
    when(trainingDao.selectById(10L)).thenReturn(target);

    ResponseEntity<Void> response = controller.deleteTraining(USER_ID, 10L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(trainingDao).clearSupersetGroup(eq(999L), any());
    verify(trainingDetailDao).softDeleteByTrainingId(10L);
    verify(trainingDao).softDeleteById(10L);
  }

  @Test
  void スーパーセットに属さない種目を削除してもグルーピング解除は呼ばれない() {
    Training target = new Training();
    target.setId(11L);
    target.setUserId(USER_ID);
    target.setSupersetGroupId(null);
    when(trainingDao.selectById(11L)).thenReturn(target);

    ResponseEntity<Void> response = controller.deleteTraining(USER_ID, 11L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(trainingDao, never()).clearSupersetGroup(anyLong(), any());
    verify(trainingDetailDao).softDeleteByTrainingId(11L);
    verify(trainingDao).softDeleteById(11L);
  }

  @Test
  void 他人のトレーニングは削除できず403を返す() {
    Training target = new Training();
    target.setId(12L);
    target.setUserId(2L);
    when(trainingDao.selectById(12L)).thenReturn(target);

    ResponseEntity<Void> response = controller.deleteTraining(USER_ID, 12L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    verify(trainingDao, never()).clearSupersetGroup(anyLong(), any());
  }
}
