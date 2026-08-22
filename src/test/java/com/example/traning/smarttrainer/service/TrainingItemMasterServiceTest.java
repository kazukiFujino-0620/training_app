package com.example.traning.smarttrainer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.TrainingMasterDao;
import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.organization.Organization;
import com.example.traning.user.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** ita1-1 未実施分: {@link TrainingItemMasterService}の種目個別追加における 公開範囲（共通/自組織/自店舗）の自動決定ロジックを検証する。 */
@ExtendWith(MockitoExtension.class)
class TrainingItemMasterServiceTest {

  @Mock private TrainingMasterDao trainingMasterDao;

  private TrainingItemMasterService service;

  @BeforeEach
  void setUp() {
    service = new TrainingItemMasterService(trainingMasterDao);
  }

  private User userWithRole(String role, Long organizationId) {
    return User.builder().userId(1).role(role).organizationId(organizationId).build();
  }

  @Test
  void addItem_ADMINは共通スコープで登録される() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(trainingMasterDao.selectItemsByPart("CHEST")).thenReturn(List.of());
    ArgumentCaptor<TrainingItemMaster> captor = ArgumentCaptor.forClass(TrainingItemMaster.class);

    service.addItem("CHEST", "新種目", admin);

    verify(trainingMasterDao).insertItem(captor.capture());
    assertThat(captor.getValue().getOrganizationId()).isEqualTo(Organization.ALL_ORGANIZATION_ID);
    assertThat(captor.getValue().getRangeOfMotionM()).isNotNull();
  }

  @Test
  void addItem_ORG_ADMINは自組織スコープで登録される() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(trainingMasterDao.selectItemsByPart("BACK")).thenReturn(List.of());
    ArgumentCaptor<TrainingItemMaster> captor = ArgumentCaptor.forClass(TrainingItemMaster.class);

    service.addItem("BACK", "自組織種目", orgAdmin);

    verify(trainingMasterDao).insertItem(captor.capture());
    assertThat(captor.getValue().getOrganizationId()).isEqualTo(1L);
  }

  @Test
  void addItem_STORE_ADMINは自店舗スコープで登録される() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 3L);
    when(trainingMasterDao.selectItemsByPart("LEG")).thenReturn(List.of());
    ArgumentCaptor<TrainingItemMaster> captor = ArgumentCaptor.forClass(TrainingItemMaster.class);

    service.addItem("LEG", "自店舗種目", storeAdmin);

    verify(trainingMasterDao).insertItem(captor.capture());
    assertThat(captor.getValue().getOrganizationId()).isEqualTo(3L);
  }

  @Test
  void addItem_USERは追加できない() {
    User user = userWithRole("ROLE_USER", 2L);

    assertThatThrownBy(() -> service.addItem("CHEST", "不正種目", user))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void addItem_表示順は既存の最大値プラス1になる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    TrainingItemMaster existing1 = new TrainingItemMaster();
    existing1.setDisplayOrder(3);
    TrainingItemMaster existing2 = new TrainingItemMaster();
    existing2.setDisplayOrder(7);
    when(trainingMasterDao.selectItemsByPart("CHEST")).thenReturn(List.of(existing1, existing2));
    ArgumentCaptor<TrainingItemMaster> captor = ArgumentCaptor.forClass(TrainingItemMaster.class);

    service.addItem("CHEST", "新種目", admin);

    verify(trainingMasterDao).insertItem(captor.capture());
    assertThat(captor.getValue().getDisplayOrder()).isEqualTo(8);
  }
}
