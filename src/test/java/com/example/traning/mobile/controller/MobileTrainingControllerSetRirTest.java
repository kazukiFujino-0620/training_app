package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.dao.UserDao;
import com.example.traning.periodization.StagnationDetectionService;
import com.example.traning.pr.service.PersonalRecordService;
import com.example.traning.restpreference.RestIntervalCalculationService;
import com.example.traning.training.Training;
import com.example.traning.training.TrainingDetail;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.training.service.TrainingService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 機能見直し-1-#18: セットのRIR（余力）の記録・変更エンドポイントを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileTrainingControllerSetRirTest {

  private static final Long USER_ID = 5L;
  private static final Long TRAINING_ID = 10L;
  private static final Long DETAIL_ID = 100L;

  @Mock private TrainingService trainingService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private PersonalRecordService personalRecordService;
  @Mock private UserDao userDao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private CalorieCalculator calorieCalculator;
  @Mock private RestIntervalCalculationService restIntervalCalculationService;
  @Mock private StagnationDetectionService stagnationDetectionService;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    MobileTrainingController controller =
        new MobileTrainingController(
            trainingService,
            trainingDao,
            trainingDetailDao,
            personalRecordService,
            userDao,
            trainingMasterDao,
            calorieCalculator,
            restIntervalCalculationService,
            stagnationDetectionService);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new MobileExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private TrainingDetail detail(String setType, Integer rir) {
    TrainingDetail d = new TrainingDetail();
    d.setId(DETAIL_ID);
    d.setTrainingId(TRAINING_ID);
    d.setSetNumber(1);
    d.setWeight(82.5);
    d.setReps(10);
    d.setSetType(setType);
    d.setIsCompleted(true);
    d.setRir(rir);
    return d;
  }

  private void givenOwnedDetail(TrainingDetail d) {
    when(trainingDetailDao.selectById(DETAIL_ID)).thenReturn(d);
    Training training = new Training();
    training.setId(TRAINING_ID);
    training.setUserId(USER_ID);
    training.setMenu("スクワット");
    when(trainingDao.selectById(TRAINING_ID)).thenReturn(training);
  }

  private TrainingDetail capturedUpdate() {
    ArgumentCaptor<TrainingDetail> captor = ArgumentCaptor.forClass(TrainingDetail.class);
    verify(trainingDetailDao).update(captor.capture());
    return captor.getValue();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2, 5})
  void 本番セットに0から5のRIRを保存できる(int rir) throws Exception {
    givenOwnedDetail(detail("MAIN", null));

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":" + rir + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rir").value(rir));

    assertThat(capturedUpdate().getRir()).isEqualTo(rir);
  }

  @Test
  void 記録済みのRIRを別の値に変更できる() throws Exception {
    givenOwnedDetail(detail("MAIN", 3));

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":1}"))
        .andExpect(status().isOk());

    assertThat(capturedUpdate().getRir()).isEqualTo(1);
  }

  @Test
  void nullを送ると未入力に戻る() throws Exception {
    givenOwnedDetail(detail("MAIN", 2));

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rir").isEmpty());

    assertThat(capturedUpdate().getRir()).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 6})
  void 範囲外のRIRは400を返し保存しない(int rir) throws Exception {
    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":" + rir + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("RIRは0〜5で指定してください"));

    verify(trainingDetailDao, never()).update(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"WARMUP", "DROP"})
  void 本番セット以外には記録できず400を返す(String setType) throws Exception {
    givenOwnedDetail(detail(setType, null));

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("RIRは本番セットにのみ記録できます"));

    verify(trainingDetailDao, never()).update(any());
  }

  @Test
  void 他人のセットは403を返す() throws Exception {
    when(trainingDetailDao.selectById(DETAIL_ID)).thenReturn(detail("MAIN", null));
    Training training = new Training();
    training.setId(TRAINING_ID);
    training.setUserId(99L);
    when(trainingDao.selectById(TRAINING_ID)).thenReturn(training);

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":2}"))
        .andExpect(status().isForbidden());

    verify(trainingDetailDao, never()).update(any());
  }

  @Test
  void 存在しないセットは404を返す() throws Exception {
    when(trainingDetailDao.selectById(DETAIL_ID)).thenReturn(null);

    mvc.perform(
            put("/api/mobile/training/sets/" + DETAIL_ID + "/rir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rir\":2}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void 重量や完了の更新ではRIRが消えない() throws Exception {
    // PATCH /sets/{id} は selectById で読んだ行をそのまま update するため、rir列が読み込まれていないと
    // セットの完了・重量変更のたびに記録済みのRIRがnullで上書きされてしまう
    givenOwnedDetail(detail("MAIN", 2));

    mvc.perform(
            patch("/api/mobile/training/sets/" + DETAIL_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isCompleted\":false}"))
        .andExpect(status().isOk());

    assertThat(capturedUpdate().getRir()).isEqualTo(2);
  }
}
