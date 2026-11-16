package com.example.traning.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import java.time.LocalDateTime;
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
  @Mock private OrganizationDao organizationDao;

  private InviteCodeService service;

  @BeforeEach
  void setUp() {
    service = new InviteCodeService(inviteCodeDao, organizationScopeResolver, organizationDao);
  }

  private User userWithRole(String role, Long organizationId) {
    return User.builder().userId(1).role(role).organizationId(organizationId).build();
  }

  @Test
  void issue_ADMINは全組織向けに発行できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(organizationScopeResolver.canAccessOrganization(admin, 999L)).thenReturn(true);

    InviteCode result = service.issue(999L, InviteCodePurpose.MEMBER, null, null, admin);

    assertThat(result.getOrganizationId()).isEqualTo(999L);
    assertThat(result.getCode()).hasSize(10);
    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINは自組織向けに発行できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 1L)).thenReturn(true);

    service.issue(1L, InviteCodePurpose.MEMBER, null, 10, orgAdmin);

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINは他組織向けには発行できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 999L)).thenReturn(false);

    assertThatThrownBy(() -> service.issue(999L, InviteCodePurpose.MEMBER, null, null, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_STORE_ADMINは発行できない() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 2L);

    assertThatThrownBy(() -> service.issue(2L, InviteCodePurpose.MEMBER, null, null, storeAdmin))
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

  // ── redeem（ita4-3: トレーナー登録での引換） ──────────────────────────────

  private InviteCode validCode() {
    InviteCode code = new InviteCode();
    code.setId(1L);
    code.setCode("ABCDEFGHIJ");
    code.setOrganizationId(5L);
    code.setUsedCount(0);
    return code;
  }

  @Test
  void redeem_有効なコードは引き換えできてusedCountが増える() {
    InviteCode code = validCode();
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    InviteCode result = service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER);

    assertThat(result.getOrganizationId()).isEqualTo(5L);
    assertThat(result.getUsedCount()).isEqualTo(1);
    verify(inviteCodeDao).update(code);
  }

  @Test
  void redeem_存在しないコードは例外() {
    when(inviteCodeDao.selectByCode("NOTFOUND")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.redeem("NOTFOUND", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_失効済みコードは例外() {
    InviteCode code = validCode();
    code.setRevokedAt(LocalDateTime.now());
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_有効期限切れのコードは例外() {
    InviteCode code = validCode();
    code.setExpiresAt(LocalDateTime.now().minusDays(1));
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_使用回数上限に達したコードは例外() {
    InviteCode code = validCode();
    code.setMaxUses(1);
    code.setUsedCount(1);
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_使用回数上限未満なら引き換えできる() {
    InviteCode code = validCode();
    code.setMaxUses(2);
    code.setUsedCount(1);
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    InviteCode result = service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER);

    assertThat(result.getUsedCount()).isEqualTo(2);
    verify(inviteCodeDao).update(code);
  }

  // ── 用途（機能見直し-1-#15・itバグ-44） ──────────────────────────────────

  @Test
  void issue_用途を指定して保存される() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 3L)).thenReturn(true);
    when(organizationDao.selectById(3L)).thenReturn(Optional.of(org(3L, "STORE")));

    InviteCode result = service.issue(3L, InviteCodePurpose.TRAINER, null, 1, orgAdmin);

    assertThat(result.getPurpose()).isEqualTo("TRAINER");
    verify(inviteCodeDao).insert(result);
  }

  @Test
  void issue_用途が無い場合は発行しない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);

    assertThatThrownBy(() -> service.issue(1L, null, null, null, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_TRAINERはトレーナー用も会員用も発行できない() {
    User trainer = userWithRole("ROLE_TRAINER", 2L);

    assertThatThrownBy(() -> service.issue(2L, InviteCodePurpose.TRAINER, null, null, trainer))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> service.issue(2L, InviteCodePurpose.MEMBER, null, null, trainer))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_STORE_ADMINはトレーナー用を発行できない() {
    User storeAdmin = userWithRole("ROLE_STORE_ADMIN", 2L);

    assertThatThrownBy(() -> service.issue(2L, InviteCodePurpose.TRAINER, null, null, storeAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void listForAdmin_TRAINERは一覧を取得できない() {
    assertThatThrownBy(() -> service.listForAdmin(userWithRole("ROLE_TRAINER", 2L)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void 新しいInviteCodeの用途の初期値は会員用() {
    assertThat(new InviteCode().getPurpose()).isEqualTo("MEMBER");
  }

  @Test
  void redeem_会員用のコードをトレーナー登録に使うと仮画面Cの文言で拒否し使用回数を増やさない() {
    InviteCode code = validCode(); // 用途は会員用（既存のコードと同じ）
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.TRAINER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("このコードはトレーナー登録には使えません。ジムの管理者から「トレーナー用」の招待コードを受け取ってください。");
    assertThat(code.getUsedCount()).isZero();
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_トレーナー用のコードを会員の新規登録に使うと拒否し使用回数を増やさない() {
    InviteCode code = validCode();
    code.setPurpose("TRAINER");
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(InviteCodeService.MEMBER_PURPOSE_MISMATCH_MESSAGE);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_トレーナー用のコードはトレーナー登録で引き換えできる() {
    InviteCode code = validCode();
    code.setPurpose("TRAINER");
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    InviteCode result = service.redeem("ABCDEFGHIJ", InviteCodePurpose.TRAINER);

    assertThat(result.getUsedCount()).isEqualTo(1);
    verify(inviteCodeDao).update(code);
  }

  @Test
  void redeem_用途が読めない値のコードはどちらの登録にも使えない() {
    InviteCode code = validCode();
    code.setPurpose("STORE_ADMIN");
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.MEMBER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.TRAINER))
        .isInstanceOf(IllegalArgumentException.class);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  @Test
  void redeem_用途違いは失効済みより先に判定しても使用回数は増えない() {
    InviteCode code = validCode();
    code.setRevokedAt(LocalDateTime.now());
    when(inviteCodeDao.selectByCode("ABCDEFGHIJ")).thenReturn(Optional.of(code));

    assertThatThrownBy(() -> service.redeem("ABCDEFGHIJ", InviteCodePurpose.TRAINER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(InviteCodeService.TRAINER_PURPOSE_MISMATCH_MESSAGE);
    verify(inviteCodeDao, never()).update(any(InviteCode.class));
  }

  // ── トレーナー用は店舗宛てだけ（itバグ-49） ──────────────────────────────────

  private static Organization org(Long id, String type) {
    Organization organization = new Organization();
    organization.setId(id);
    organization.setType(type);
    return organization;
  }

  @Test
  void issue_ADMINはトレーナー用を店舗宛てに発行できる() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(organizationScopeResolver.canAccessOrganization(admin, 3L)).thenReturn(true);
    when(organizationDao.selectById(3L)).thenReturn(Optional.of(org(3L, "STORE")));

    InviteCode result = service.issue(3L, InviteCodePurpose.TRAINER, null, null, admin);

    assertThat(result.getOrganizationId()).isEqualTo(3L);
    verify(inviteCodeDao).insert(result);
  }

  @Test
  void issue_ADMINはトレーナー用を組織宛てには発行できない() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(organizationScopeResolver.canAccessOrganization(admin, 1L)).thenReturn(true);
    when(organizationDao.selectById(1L)).thenReturn(Optional.of(org(1L, "GYM")));

    assertThatThrownBy(() -> service.issue(1L, InviteCodePurpose.TRAINER, null, null, admin))
        .isInstanceOf(InviteCodeIssueRejectedException.class)
        .hasMessage("トレーナー用の招待コードは、店舗を選んで発行してください。");
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINはトレーナー用を自組織の店舗宛てに発行できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 3L)).thenReturn(true);
    when(organizationDao.selectById(3L)).thenReturn(Optional.of(org(3L, "STORE")));

    service.issue(3L, InviteCodePurpose.TRAINER, null, 1, orgAdmin);

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINはトレーナー用を自組織のジム宛てには発行できない() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 1L)).thenReturn(true);
    when(organizationDao.selectById(1L)).thenReturn(Optional.of(org(1L, "GYM")));

    assertThatThrownBy(() -> service.issue(1L, InviteCodePurpose.TRAINER, null, 1, orgAdmin))
        .isInstanceOf(InviteCodeIssueRejectedException.class)
        .hasMessage(InviteCodeService.TRAINER_REQUIRES_STORE_MESSAGE);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_トレーナー用は存在しない組織宛てには発行できない() {
    User admin = userWithRole("ROLE_ADMIN", 2L);
    when(organizationScopeResolver.canAccessOrganization(admin, 999L)).thenReturn(true);
    when(organizationDao.selectById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.issue(999L, InviteCodePurpose.TRAINER, null, null, admin))
        .isInstanceOf(InviteCodeIssueRejectedException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_ORG_ADMINは他組織の店舗宛てにはトレーナー用でも従来どおり権限エラー() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 99L)).thenReturn(false);

    assertThatThrownBy(() -> service.issue(99L, InviteCodePurpose.TRAINER, null, null, orgAdmin))
        .isInstanceOf(ResponseStatusException.class);
    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void issue_会員用は組織宛てにも店舗宛てにも従来どおり発行できる() {
    User orgAdmin = userWithRole("ROLE_ORG_ADMIN", 1L);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 1L)).thenReturn(true);
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, 3L)).thenReturn(true);

    service.issue(1L, InviteCodePurpose.MEMBER, null, null, orgAdmin);
    service.issue(3L, InviteCodePurpose.MEMBER, null, null, orgAdmin);

    verify(inviteCodeDao, org.mockito.Mockito.times(2)).insert(any(InviteCode.class));
    verify(organizationDao, never()).selectById(any());
  }
}
