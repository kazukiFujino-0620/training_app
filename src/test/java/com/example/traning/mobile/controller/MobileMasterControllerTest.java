package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingItemFormGuideDao;
import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.mobile.dto.TrainingItemMasterResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 機能見直し-1-#2: 種目マスタ一覧APIへのhasFormGuide付与を検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileMasterControllerTest {

  @Mock private TrainingMasterDao trainingMasterDao;
  @Mock private TrainingItemFormGuideDao formGuideDao;

  private MobileMasterController controller;

  @BeforeEach
  void setUp() {
    controller = new MobileMasterController(trainingMasterDao, formGuideDao);
  }

  private TrainingItemMaster item(Long id, String partCode, String itemName) {
    TrainingItemMaster item = new TrainingItemMaster();
    item.setId(id);
    item.setPartCode(partCode);
    item.setItemName(itemName);
    item.setDisplayOrder(1);
    item.setMasterFlg(1);
    item.setOrganizationId(0L);
    return item;
  }

  @Test
  void getItems_フォーム解説が登録済みの種目のみhasFormGuideがtrueになる() {
    when(trainingMasterDao.selectActiveItems())
        .thenReturn(List.of(item(1L, "LEG", "バックスクワット"), item(2L, "CHEST", "ベンチプレス")));
    when(formGuideDao.selectAllItemNamesWithGuide()).thenReturn(List.of("バックスクワット"));

    ResponseEntity<List<TrainingItemMasterResponse>> response = controller.getItems(null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<TrainingItemMasterResponse> body = response.getBody();
    assertThat(body).hasSize(2);
    assertThat(body.get(0).getItemName()).isEqualTo("バックスクワット");
    assertThat(body.get(0).isHasFormGuide()).isTrue();
    assertThat(body.get(1).getItemName()).isEqualTo("ベンチプレス");
    assertThat(body.get(1).isHasFormGuide()).isFalse();
  }

  @Test
  void getItems_partCode指定時はselectActiveItemsByPartを使う() {
    when(trainingMasterDao.selectActiveItemsByPart("LEG"))
        .thenReturn(List.of(item(1L, "LEG", "バックスクワット")));
    when(formGuideDao.selectAllItemNamesWithGuide()).thenReturn(List.of());

    ResponseEntity<List<TrainingItemMasterResponse>> response = controller.getItems("LEG");

    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().get(0).isHasFormGuide()).isFalse();
  }
}
