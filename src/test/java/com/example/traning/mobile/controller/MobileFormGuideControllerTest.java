package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingItemFormGuideDao;
import com.example.traning.entity.TrainingItemFormCaution;
import com.example.traning.entity.TrainingItemFormGuide;
import com.example.traning.mobile.dto.FormGuideResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 機能見直し-1-#2: 種目フォーム解説取得APIを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileFormGuideControllerTest {

  @Mock private TrainingItemFormGuideDao formGuideDao;

  private MobileFormGuideController controller;

  @BeforeEach
  void setUp() {
    controller = new MobileFormGuideController(formGuideDao);
  }

  @Test
  void get_登録済みの種目は画像URLと注意事項を含めて200を返す() {
    TrainingItemFormGuide guide = new TrainingItemFormGuide();
    guide.setItemName("バックスクワット");
    guide.setImageUrl("/images/exercise-guides/back-squat.jpg");
    guide.setVideoUrl("https://example.com/video");
    guide.setJointAngleNote("一般的な目安であり個人差があります");

    TrainingItemFormCaution caution = new TrainingItemFormCaution();
    caution.setItemName("バックスクワット");
    caution.setDisplayOrder(1);
    caution.setTitle("ニーイン");
    caution.setDescription("膝が内側に入る");
    caution.setReason("一般的に大腿部外側の筋力不足等が指摘されている");

    when(formGuideDao.selectByItemName("バックスクワット")).thenReturn(Optional.of(guide));
    when(formGuideDao.selectCautionsByItemName("バックスクワット")).thenReturn(List.of(caution));

    ResponseEntity<FormGuideResponse> response = controller.get("バックスクワット");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    FormGuideResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getItemName()).isEqualTo("バックスクワット");
    assertThat(body.getImageUrl()).isEqualTo("/images/exercise-guides/back-squat.jpg");
    assertThat(body.getCautions()).hasSize(1);
    assertThat(body.getCautions().get(0).getTitle()).isEqualTo("ニーイン");
  }

  @Test
  void get_未登録の種目は404を返す() {
    when(formGuideDao.selectByItemName("存在しない種目")).thenReturn(Optional.empty());

    ResponseEntity<FormGuideResponse> response = controller.get("存在しない種目");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
