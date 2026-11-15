package com.example.traning.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.traning.user.User;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * 機能見直し-1-#15: {@link MemberAccessPolicy} の役割×会員との関係ごとの判定を検証する。
 *
 * <p>組織: ジム1 → 店舗10（トレーナーの店舗）・店舗20（同じジムの別店舗）、ジム2 → 店舗30。 スコープの解決は実物の {@link
 * OrganizationScopeResolver} を使い、DAOのみモックする。
 */
@ExtendWith(MockitoExtension.class)
class MemberAccessPolicyTest {

  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;

  private MemberAccessPolicy policy;

  private static final long GYM_1 = 1L;
  private static final long STORE_10 = 10L;
  private static final long STORE_20 = 20L;
  private static final long STORE_30 = 30L;

  @BeforeEach
  void setUp() {
    policy =
        new MemberAccessPolicy(new OrganizationScopeResolver(organizationDao, userStoreAccessDao));
    Organization s10 = new Organization();
    s10.setId(STORE_10);
    Organization s20 = new Organization();
    s20.setId(STORE_20);
    lenient()
        .when(organizationDao.selectByParentOrganizationId(GYM_1))
        .thenReturn(List.of(s10, s20));
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(anyLong()))
        .thenReturn(List.of());
  }

  private static User user(int id, String role, Long organizationId, Long assignedTrainerId) {
    return User.builder()
        .userId(id)
        .role(role)
        .organizationId(organizationId)
        .assignedTrainerId(assignedTrainerId)
        .build();
  }

  private final User trainer = user(1, "ROLE_TRAINER", STORE_10, null);

  // ── トレーナー ────────────────────────────────────────────────

  @Test
  void トレーナー_担当会員は指導できる() {
    User member = user(100, "ROLE_USER", STORE_10, 1L);
    assertThat(policy.resolve(trainer, member)).isEqualTo(MemberAccessLevel.FULL);
  }

  @Test
  void トレーナー_担当会員が別の店舗に所属していても担当なので指導できる() {
    User member = user(100, "ROLE_USER", STORE_20, 1L);
    assertThat(policy.resolve(trainer, member)).isEqualTo(MemberAccessLevel.FULL);
  }

  @Test
  void トレーナー_同じ店舗の担当外の会員は記録の閲覧のみ() {
    User otherAssigned = user(101, "ROLE_USER", STORE_10, 99L);
    User unassigned = user(102, "ROLE_USER", STORE_10, null);
    assertThat(policy.resolve(trainer, otherAssigned)).isEqualTo(MemberAccessLevel.RECORDS_ONLY);
    assertThat(policy.resolve(trainer, unassigned)).isEqualTo(MemberAccessLevel.RECORDS_ONLY);
    assertThat(MemberAccessLevel.RECORDS_ONLY.canViewRecords()).isTrue();
    assertThat(MemberAccessLevel.RECORDS_ONLY.canCoach()).isFalse();
  }

  @Test
  void トレーナー_同じジムの別の店舗の会員は見られない() {
    User member = user(103, "ROLE_USER", STORE_20, null);
    assertThat(policy.resolve(trainer, member)).isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void トレーナー_他のジムの会員は見られない() {
    User member = user(104, "ROLE_USER", STORE_30, 99L);
    assertThat(policy.resolve(trainer, member)).isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void トレーナー_同じ店舗でもスタッフの記録は見られない() {
    assertThat(policy.resolve(trainer, user(105, "ROLE_STORE_ADMIN", STORE_10, null)))
        .isEqualTo(MemberAccessLevel.NONE);
    assertThat(policy.resolve(trainer, user(106, "ROLE_TRAINER", STORE_10, null)))
        .isEqualTo(MemberAccessLevel.NONE);
    assertThat(policy.resolve(trainer, user(107, "ROLE_ORG_ADMIN", STORE_10, null)))
        .isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void トレーナー_スタッフが自分に割り当てられていても会員でなければ見られない() {
    assertThat(policy.resolve(trainer, user(108, "ROLE_STORE_ADMIN", STORE_10, 1L)))
        .isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void トレーナー_兼任店舗の仕組みはトレーナーには使わない() {
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(1L))
        .thenReturn(List.of(STORE_20));
    User member = user(109, "ROLE_USER", STORE_20, null);
    assertThat(policy.resolve(trainer, member)).isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void トレーナー_自分自身はFULL() {
    assertThat(policy.resolve(trainer, trainer)).isEqualTo(MemberAccessLevel.FULL);
  }

  @Test
  void requireRecordsView_範囲外なら403() {
    User member = user(103, "ROLE_USER", STORE_20, null);
    assertThatThrownBy(() -> policy.requireRecordsView(trainer, member))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void isVisibleToTrainer_一覧に出すのは担当会員と自分の店舗の会員だけ() {
    Set<Long> accessible = Set.of(STORE_10);
    assertThat(policy.isVisibleToTrainer(trainer, user(100, "ROLE_USER", STORE_10, 1L), accessible))
        .isTrue();
    assertThat(
            policy.isVisibleToTrainer(trainer, user(101, "ROLE_USER", STORE_10, 99L), accessible))
        .isTrue();
    assertThat(policy.isVisibleToTrainer(trainer, user(102, "ROLE_USER", STORE_20, 1L), accessible))
        .isTrue();
    assertThat(
            policy.isVisibleToTrainer(trainer, user(103, "ROLE_USER", STORE_20, null), accessible))
        .isFalse();
    assertThat(
            policy.isVisibleToTrainer(
                trainer, user(105, "ROLE_STORE_ADMIN", STORE_10, null), accessible))
        .isFalse();
  }

  // ── 管理者（今の権限を維持） ───────────────────────────────────────

  @Test
  void 店舗管理者_自店舗の会員は担当に関係なくFULL_他の店舗はNONE() {
    User storeAdmin = user(2, "ROLE_STORE_ADMIN", STORE_10, null);
    assertThat(policy.resolve(storeAdmin, user(101, "ROLE_USER", STORE_10, 99L)))
        .isEqualTo(MemberAccessLevel.FULL);
    assertThat(policy.resolve(storeAdmin, user(103, "ROLE_USER", STORE_20, null)))
        .isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void 店舗管理者_兼任店舗の会員はFULL() {
    when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(2L)).thenReturn(List.of(STORE_20));
    User storeAdmin = user(2, "ROLE_STORE_ADMIN", STORE_10, null);
    assertThat(policy.resolve(storeAdmin, user(103, "ROLE_USER", STORE_20, null)))
        .isEqualTo(MemberAccessLevel.FULL);
  }

  @Test
  void ジム管理者_ジム内の全店舗はFULL_他のジムはNONE() {
    User orgAdmin = user(3, "ROLE_ORG_ADMIN", GYM_1, null);
    assertThat(policy.resolve(orgAdmin, user(101, "ROLE_USER", STORE_10, null)))
        .isEqualTo(MemberAccessLevel.FULL);
    assertThat(policy.resolve(orgAdmin, user(103, "ROLE_USER", STORE_20, null)))
        .isEqualTo(MemberAccessLevel.FULL);
    assertThat(policy.resolve(orgAdmin, user(104, "ROLE_USER", STORE_30, null)))
        .isEqualTo(MemberAccessLevel.NONE);
  }

  @Test
  void システム管理者_全組織FULL() {
    User admin = user(4, "ROLE_ADMIN", STORE_10, null);
    assertThat(policy.resolve(admin, user(104, "ROLE_USER", STORE_30, null)))
        .isEqualTo(MemberAccessLevel.FULL);
  }

  // ── 会員・不正値 ────────────────────────────────────────────

  @Test
  void 会員_他の会員は見られない_自分はFULL() {
    User member = user(100, "ROLE_USER", STORE_10, null);
    assertThat(policy.resolve(member, user(101, "ROLE_USER", STORE_10, null)))
        .isEqualTo(MemberAccessLevel.NONE);
    assertThat(policy.resolve(member, member)).isEqualTo(MemberAccessLevel.FULL);
  }

  @Test
  void 不明な役割やnullは見られない() {
    User unknown = user(5, "ROLE_SOMETHING", STORE_10, null);
    User member = user(101, "ROLE_USER", STORE_10, 5L);
    assertThat(policy.resolve(unknown, member)).isEqualTo(MemberAccessLevel.NONE);
    assertThat(policy.resolve(null, member)).isEqualTo(MemberAccessLevel.NONE);
    assertThat(policy.resolve(trainer, null)).isEqualTo(MemberAccessLevel.NONE);
  }
}
