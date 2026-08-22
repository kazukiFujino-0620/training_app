package com.example.traning.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * ita1-1 未実施分: {@link InviteCodeService}の招待コード発行・失効における
 * 操作者ロール別の権限制御を検証する。DAOはMockitoでモックし、DBには依存しない。
 */
@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

  @Mock private InviteCodeDao inviteCodeDao;
  @Mock private OrganizationScopeResolver organizationScopeResolver;

  private InviteCodeService service;

  @BeforeEach
  void setUp() {
    service = new InviteCodeService(inviteCodeDao, organizationScopeResolver);
  }

  private User userWithRole(String role, Long organizationId) {
    return User.builder().userId(1).role(role).organizationId(organizationId).build();
  }

  @Test
  void issue_ADMINは全組織向けに発行できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(organizationScopeResolver.canAccessOrganization(admin, 999L)).thenReturn(true);

    InviteCode result = service.issue(999L, null, null, admin);

    assertThat(result.getOrganizationId()).isEqualTo(999L);
    assertThat(result.getCode()).hasSize(10);
    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINは自組織向けに発行できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 1L)).thenReturn(true);

    service.issue(1L, null, 10, orgAdmin);

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINは他組織向けには発行できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 999L)).thenReturn(false);

    assertThatThrownBy(() -> service.issue(999L, null, null, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_STORE_ADMINは発行できない() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 2L);

    assertThatThrownBy(() -> service.issue(2L, null, null, storeAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void listForAdmin_ADMINは全件取得する() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(inviteCodeDao.selectAll()).thenReturn(List.of(new InviteCode()));

    List<InviteCode> result = service.listForAdmin(admin);

    assertThat(result).hasSize(1);
  }

  @Test
  void listForAdmin_ORG_ADMINはアクセス可能組織のみ取得する() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(orgAdmin))
        .thenReturn(Set.of(1L, 2L));
    when(inviteCodeDao.selectByOrganizationIds(any())).thenReturn(List.of());

    service.listForAdmin(orgAdmin);

    verify(inviteCodeDao).selectByOrganizationIds(any());
  }

  @Test
  void revoke_アクセス可能な組織のコードは失効できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    InviteCode code = new InviteCode();
    code.setId(1L);
    code.setOrganizationId(5L);
    when(inviteCodeDao.selectById(1L)).thenReturn(Optional.of(code));
    when(organizationScopeResolver.canAccessOrganization(admin, 5L)).thenReturn(true);

    service.revoke(1L, admin);

    verify(inviteCodeDao).update(any(InviteCode.class));
  }

  @Test
  void revoke_アクセス不可な組織のコードは失効できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    InviteCode code = new InviteCode();
    code.setId(1L);
    code.setOrganizationId(999L);
    when(inviteCodeDao.selectById(1L)).thenReturn(Optional.of(code));
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 999L)).thenReturn(false);

    assertThatThrownBy(() -> service.revoke(1L, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }
}
