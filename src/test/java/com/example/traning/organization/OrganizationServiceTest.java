package com.example.traning.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * ita1-1 未実施分: {@link OrganizationService}の組織・店舗作成/編集における
 * 操作者ロール別の権限制御を検証する。DAOはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

  @Mock private OrganizationDao organizationDao;
  @Mock private UserDao userDao;

  private OrganizationService service;

  @BeforeEach
  void setUp() {
    service = new OrganizationService(organizationDao, userDao);
  }

  private User userWithRole(String role, Long organizationId) {
    return User.builder().userId(1).role(role).organizationId(organizationId).build();
  }

  @Test
  void createGym_ADMINは作成できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);

    service.createGym("新規ジム", admin);

    verify(organizationDao).insert(any(Organization.class));
  }

  @Test
  void createGym_ORG_ADMINは作成できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);

    assertThatThrownBy(() -> service.createGym("新規ジム", orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createStore_ORG_ADMINは自組織配下なら作成できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    Organization gym = new Organization();
    gym.setId(1L);
    gym.setType(OrganizationType.GYM.name());
    when(organizationDao.selectById(1L)).thenReturn(Optional.of(gym));

    service.createStore("新規店舗", 1L, orgAdmin);

    verify(organizationDao).insert(any(Organization.class));
  }

  @Test
  void createStore_ORG_ADMINは他組織配下には作成できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);

    assertThatThrownBy(() -> service.createStore("不正店舗", 999L, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void createStore_ADMINは任意のGYM配下に作成できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    Organization gym = new Organization();
    gym.setId(5L);
    gym.setType(OrganizationType.GYM.name());
    when(organizationDao.selectById(5L)).thenReturn(Optional.of(gym));

    service.createStore("新規店舗", 5L, admin);

    verify(organizationDao).insert(any(Organization.class));
  }

  @Test
  void createStore_STORE_ADMINは作成できない() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 2L);

    assertThatThrownBy(() -> service.createStore("不正店舗", 1L, storeAdmin))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void getOrganizationTree_ADMINは全GYMを取得する() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    Organization gymA = new Organization();
    gymA.setId(1L);
    gymA.setName("Aジム");
    gymA.setType(OrganizationType.GYM.name());
    Organization store = new Organization();
    store.setType(OrganizationType.STORE.name());
    when(organizationDao.selectAll()).thenReturn(List.of(gymA, store));
    when(organizationDao.selectByParentOrganizationId(1L)).thenReturn(List.of());
    when(userDao.selectAll()).thenReturn(List.of());

    List<OrganizationService.OrganizationNode> tree = service.getOrganizationTree(admin);

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).name()).isEqualTo("Aジム");
  }

  @Test
  void getOrganizationTree_STORE_ADMINはアクセス不可() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 2L);

    assertThatThrownBy(() -> service.getOrganizationTree(storeAdmin))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void renameOrganization_ORG_ADMINは自組織配下の店舗を編集できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    Organization store = new Organization();
    store.setId(10L);
    store.setParentOrganizationId(1L);
    when(organizationDao.selectById(10L)).thenReturn(Optional.of(store));

    service.renameOrganization(10L, "新名称", orgAdmin);

    verify(organizationDao).update(any(Organization.class));
  }

  @Test
  void renameOrganization_ORG_ADMINは他組織の店舗を編集できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    Organization otherStore = new Organization();
    otherStore.setId(20L);
    otherStore.setParentOrganizationId(999L);
    when(organizationDao.selectById(20L)).thenReturn(Optional.of(otherStore));

    assertThatThrownBy(() -> service.renameOrganization(20L, "不正な名称変更", orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
  }
}
