package com.example.traning.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.TrainingService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/** itバグ-14: テンプレート適用時、当日すでに同名種目が登録済みならスキップし重複登録しないことを検証する。 */
@ExtendWith(MockitoExtension.class)
class TemplateControllerApplyTest {

  @Mock private TrainingTemplateDao trainingTemplateDao;
  @Mock private TrainingTemplateItemDao trainingTemplateItemDao;
  @Mock private TrainingService trainingService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private Principal principal;

  private TemplateController controller;

  @BeforeEach
  void setUp() {
    controller =
        new TemplateController(
            trainingTemplateDao,
            trainingTemplateItemDao,
            trainingService,
            trainingDao,
            trainingDetailDao,
            trainingMasterDao);
    lenient().when(principal.getName()).thenReturn("taro@example.com");
    lenient().when(trainingService.getUserIdByEmail("taro@example.com")).thenReturn(1L);
    lenient()
        .when(trainingDao.selectRecentSessionsByItem(anyLong(), anyString(), any(), anyInt()))
        .thenReturn(List.of());
  }

  private TrainingTemplate template(Long id, String... itemNames) {
    TrainingTemplate t = new TrainingTemplate();
    t.setId(id);
    t.setUserId(1L);
    t.setPartCode("CHEST");
    List<TrainingTemplateItem> items =
        java.util.Arrays.stream(itemNames)
            .map(
                name -> {
                  TrainingTemplateItem item = new TrainingTemplateItem();
                  item.setItemName(name);
                  item.setSetNumber(1);
                  item.setDisplayOrder(0);
                  return item;
                })
            .toList();
    when(trainingTemplateItemDao.selectByTemplateId(id)).thenReturn(items);
    when(trainingTemplateDao.selectById(id)).thenReturn(Optional.of(t));
    return t;
  }

  private Training existingTraining(String menu) {
    Training t = new Training();
    t.setMenu(menu);
    return t;
  }

  @Test
  void applyTemplate_当日未登録の種目のみの場合は全件登録される() {
    template(10L, "ベンチプレス", "ダンベルフライ");
    when(trainingService.getFullTrainingData(1L, LocalDate.of(2026, 8, 31))).thenReturn(List.of());

    ResponseEntity<Map<String, Object>> response =
        controller.applyTemplate(10L, Map.of("date", "2026-08-31"), principal);

    verify(trainingService, times(2)).save(any(Training.class), eq(principal));
    assertThat((List<?>) response.getBody().get("duplicateItems")).isEmpty();
  }

  @Test
  void applyTemplate_当日既に登録済みの種目はスキップされ重複登録されない() {
    template(10L, "ベンチプレス", "ダンベルフライ");
    when(trainingService.getFullTrainingData(1L, LocalDate.of(2026, 8, 31)))
        .thenReturn(List.of(existingTraining("ベンチプレス")));

    ResponseEntity<Map<String, Object>> response =
        controller.applyTemplate(10L, Map.of("date", "2026-08-31"), principal);

    ArgumentCaptor<Training> captor = ArgumentCaptor.forClass(Training.class);
    verify(trainingService, times(1)).save(captor.capture(), eq(principal));
    assertThat(captor.getValue().getMenu()).isEqualTo("ダンベルフライ");

    @SuppressWarnings("unchecked")
    List<String> duplicateItems = (List<String>) response.getBody().get("duplicateItems");
    assertThat(duplicateItems).containsExactly("ベンチプレス");
  }

  @Test
  void applyTemplate_全種目が登録済みの場合は1件も保存しない() {
    template(10L, "ベンチプレス");
    when(trainingService.getFullTrainingData(1L, LocalDate.of(2026, 8, 31)))
        .thenReturn(List.of(existingTraining("ベンチプレス")));

    controller.applyTemplate(10L, Map.of("date", "2026-08-31"), principal);

    verify(trainingService, never()).save(any(Training.class), eq(principal));
  }
}
