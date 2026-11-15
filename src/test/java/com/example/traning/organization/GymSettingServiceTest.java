package com.example.traning.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#15 Q15-2: ジムの設定（初期値・ジムの解決・変更できる人）を検証する。 */
@ExtendWith(MockitoExtension.class)
class GymSettingServiceTest {

  @Mock private GymSettingDao gymSettingDao;
  @Mock private OrganizationDao organizationDao;

  private GymSettingService service;

  private static Organization org(long id, String type, Long parentId) {
    Organization o = new Organization();
    o.setId(id);
    o.setType(type);
    o.setParentOrganizationId(parentId);
    return o;
  }

  private final Organization gym1 = org(1L, "GYM", null);
  private final Organization gym2 = org(2L, "GYM", null);
  private final Organization store10 = org(10L, "STORE", 1L);

  @BeforeEach
  void setUp() {
    service = new GymSettingService(gymSettingDao, organizationDao);
    lenient().when(organizationDao.selectById(1L)).thenReturn(Optional.of(gym1));
    lenient().when(organizationDao.selectById(2L)).thenReturn(Optional.of(gym2));
    lenient().when(organizationDao.selectById(10L)).thenReturn(Optional.of(store10));
    lenient().when(organizationDao.selectById(0L)).thenReturn(Optional.of(org(0L, "ALL", null)));
  }

  private static User user(int id, String role, Long organizationId) {
    return User.builder().userId(id).role(role).organizationId(organizationId).build();
  }

  @Test
  void resolveGymId_店舗は親のジム_ジムはそれ自身_ALLや不明は空() {
    assertThat(service.resolveGymId(10L)).contains(1L);
    assertThat(service.resolveGymId(1L)).contains(1L);
    assertThat(service.resolveGymId(0L)).isEmpty();
    assertThat(service.resolveGymId(null)).isEmpty();
  }

  @Test
  void 設定の行が無いジムは初期値の見られない() {
    when(gymSettingDao.selectByOrganizationId(1L)).thenReturn(Optional.empty());
    assertThat(service.isStaffHealthInfoVisible(10L)).isFalse();
  }

  @Test
  void 設定が見られるならその店舗の会員について見られる() {
    GymSetting setting = new GymSetting();
    setting.setOrganizationId(1L);
    setting.setStaffHealthInfoVisible(true);
    when(gymSettingDao.selectByOrganizationId(1L)).thenReturn(Optional.of(setting));
    assertThat(service.isStaffHealthInfoVisible(10L)).isTrue();
  }

  @Test
  void ジムが解決できない組織は見られない() {
    assertThat(service.isStaffHealthInfoVisible(0L)).isFalse();
    verify(gymSettingDao, never()).selectByOrganizationId(any());
  }

  @Test
  void ジム管理者は自分のジムの設定を新規保存できる() {
    User orgAdmin = user(3, "ROLE_ORG_ADMIN", 1L);
    when(gymSettingDao.selectByOrganizationId(1L)).thenReturn(Optional.empty());

    service.updateStaffHealthInfoVisible(orgAdmin, 1L, true);

    ArgumentCaptor<GymSetting> captor = ArgumentCaptor.forClass(GymSetting.class);
    verify(gymSettingDao).insert(captor.capture());
    assertThat(captor.getValue().getOrganizationId()).isEqualTo(1L);
    assertThat(captor.getValue().getStaffHealthInfoVisible()).isTrue();
    assertThat(captor.getValue().getUpdatedBy()).isEqualTo(3L);
  }

  @Test
  void ジム管理者は既存の設定を更新できる() {
    User orgAdmin = user(3, "ROLE_ORG_ADMIN", 1L);
    GymSetting existing = new GymSetting();
    existing.setOrganizationId(1L);
    existing.setStaffHealthInfoVisible(true);
    when(gymSettingDao.selectByOrganizationId(1L)).thenReturn(Optional.of(existing));

    service.updateStaffHealthInfoVisible(orgAdmin, 1L, false);

    verify(gymSettingDao).update(existing);
    assertThat(existing.getStaffHealthInfoVisible()).isFalse();
  }

  @Test
  void ジム管理者は他のジムの設定を変えられない() {
    User orgAdmin = user(3, "ROLE_ORG_ADMIN", 1L);
    assertThatThrownBy(() -> service.updateStaffHealthInfoVisible(orgAdmin, 2L, true))
        .isInstanceOf(ResponseStatusException.class);
    verify(gymSettingDao, never()).insert(any());
    verify(gymSettingDao, never()).update(any());
  }

  @Test
  void 店舗管理者とトレーナーと会員は設定を変えられない() {
    for (String role : List.of("ROLE_STORE_ADMIN", "ROLE_TRAINER", "ROLE_USER")) {
      User actor = user(5, role, 10L);
      assertThat(service.listEditableGyms(actor)).isEmpty();
      assertThatThrownBy(() -> service.updateStaffHealthInfoVisible(actor, 1L, true))
          .isInstanceOf(ResponseStatusException.class);
    }
    verify(gymSettingDao, never()).insert(any());
    verify(gymSettingDao, never()).update(any());
  }

  @Test
  void システム管理者は全ジムを変えられる() {
    when(organizationDao.selectAll()).thenReturn(List.of(gym1, gym2, store10));
    User admin = user(4, "ROLE_ADMIN", 1L);
    assertThat(service.listEditableGyms(admin)).containsExactly(gym1, gym2);
    when(gymSettingDao.selectByOrganizationId(2L)).thenReturn(Optional.empty());

    service.updateStaffHealthInfoVisible(admin, 2L, true);

    verify(gymSettingDao).insert(any(GymSetting.class));
  }

  @Test
  void 新しいGymSettingの初期値は見られない() {
    assertThat(new GymSetting().getStaffHealthInfoVisible()).isFalse();
  }
}
