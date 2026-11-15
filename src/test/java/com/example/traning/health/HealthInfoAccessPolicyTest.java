package com.example.traning.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.traning.audit.AuditLogService;
import com.example.traning.organization.GymSettingService;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.User;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#15 Q15-2: 健康に関わる情報を誰が見られるか（ジムの設定あり／なし、担当／担当外）と、 管理者が設定にもとづいて見たときの監査ログを検証する。 */
@ExtendWith(MockitoExtension.class)
class HealthInfoAccessPolicyTest {

  @Mock private OrganizationScopeResolver organizationScopeResolver;
  @Mock private GymSettingService gymSettingService;
  @Mock private AuditLogService auditLogService;

  private HealthInfoAccessPolicy policy;

  private static final long STORE_10 = 10L;
  private static final long STORE_30 = 30L;

  @BeforeEach
  void setUp() {
    policy =
        new HealthInfoAccessPolicy(organizationScopeResolver, gymSettingService, auditLogService);
    // 範囲: 店舗10のみ（ジム管理者・店舗管理者とも同じ扱いでよい。範囲の解決自体はMemberAccessPolicyTestで検証）
    lenient()
        .when(organizationScopeResolver.canAccessOrganization(any(), eq(STORE_10)))
        .thenReturn(true);
    lenient()
        .when(organizationScopeResolver.canAccessOrganization(any(), eq(STORE_30)))
        .thenReturn(false);
  }

  private void gymSettingVisible(boolean visible) {
    lenient().when(gymSettingService.isStaffHealthInfoVisible(STORE_10)).thenReturn(visible);
    lenient().when(gymSettingService.isStaffHealthInfoVisible(STORE_30)).thenReturn(visible);
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
  private final User storeAdmin = user(2, "ROLE_STORE_ADMIN", STORE_10, null);
  private final User orgAdmin = user(3, "ROLE_ORG_ADMIN", 1L, null);
  private final User admin = user(4, "ROLE_ADMIN", 1L, null);

  @Test
  void 本人は見られる() {
    User member = user(100, "ROLE_USER", STORE_10, null);
    assertThat(policy.decide(member, member))
        .isEqualTo(new HealthInfoAccessPolicy.Decision(true, false));
  }

  @Test
  void 担当トレーナーは設定に関係なく見られて監査ログは不要() {
    gymSettingVisible(false);
    User member = user(100, "ROLE_USER", STORE_10, 1L);
    assertThat(policy.decide(trainer, member))
        .isEqualTo(new HealthInfoAccessPolicy.Decision(true, false));
  }

  @Test
  void 担当外のトレーナーは設定が見られるでも見られない() {
    gymSettingVisible(true);
    User sameStore = user(101, "ROLE_USER", STORE_10, 99L);
    User unassigned = user(102, "ROLE_USER", STORE_10, null);
    assertThat(policy.canView(trainer, sameStore)).isFalse();
    assertThat(policy.canView(trainer, unassigned)).isFalse();
  }

  @Test
  void 管理者_設定が初期値の見られないなら店舗管理者もジム管理者もシステム管理者も見られない() {
    gymSettingVisible(false);
    User member = user(101, "ROLE_USER", STORE_10, 99L);
    assertThat(policy.canView(storeAdmin, member)).isFalse();
    assertThat(policy.canView(orgAdmin, member)).isFalse();
    assertThat(policy.canView(admin, member)).isFalse();
  }

  @Test
  void 管理者_設定が見られるなら範囲内の会員を見られて監査ログが必要() {
    gymSettingVisible(true);
    User member = user(101, "ROLE_USER", STORE_10, 99L);
    HealthInfoAccessPolicy.Decision expected = new HealthInfoAccessPolicy.Decision(true, true);
    assertThat(policy.decide(storeAdmin, member)).isEqualTo(expected);
    assertThat(policy.decide(orgAdmin, member)).isEqualTo(expected);
    assertThat(policy.decide(admin, member)).isEqualTo(expected);
  }

  @Test
  void 管理者_設定が見られるでも範囲外の会員は見られない() {
    gymSettingVisible(true);
    User otherGymMember = user(103, "ROLE_USER", STORE_30, null);
    assertThat(policy.canView(storeAdmin, otherGymMember)).isFalse();
    assertThat(policy.canView(orgAdmin, otherGymMember)).isFalse();
  }

  @Test
  void 管理者_見られない設定でも自分が担当なら見られて監査ログは不要() {
    gymSettingVisible(false);
    User member = user(101, "ROLE_USER", STORE_10, 2L);
    assertThat(policy.decide(storeAdmin, member))
        .isEqualTo(new HealthInfoAccessPolicy.Decision(true, false));
  }

  @Test
  void 他の会員は見られない() {
    User member = user(100, "ROLE_USER", STORE_10, null);
    User other = user(101, "ROLE_USER", STORE_10, null);
    assertThat(policy.canView(member, other)).isFalse();
  }

  @Test
  void requireViewAndRecord_見られない場合は403で監査ログも残さない() {
    gymSettingVisible(false);
    User member = user(101, "ROLE_USER", STORE_10, 99L);
    assertThatThrownBy(() -> policy.requireViewAndRecord(storeAdmin, member, "health_sleep"))
        .isInstanceOf(ResponseStatusException.class);
    verify(auditLogService, never())
        .record(any(), anyString(), anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  void requireViewAndRecord_管理者が設定にもとづいて見たら監査ログに記録する() {
    gymSettingVisible(true);
    User member = user(101, "ROLE_USER", STORE_10, 99L);

    policy.requireViewAndRecord(orgAdmin, member, "health_sleep");

    verify(auditLogService)
        .record(
            eq(3L),
            eq(HealthInfoAccessPolicy.AUDIT_ACTION),
            eq("health_sleep"),
            eq(101L),
            anyString(),
            anyString());
  }

  @Test
  void requireViewAndRecord_担当トレーナーの閲覧は監査ログに記録しない() {
    User member = user(100, "ROLE_USER", STORE_10, 1L);

    policy.requireViewAndRecord(trainer, member, "health_sleep");

    verify(auditLogService, never())
        .record(any(), anyString(), anyString(), anyLong(), anyString(), anyString());
  }

  @Test
  void スコープ解決の結果が空でも例外にならず見られない() {
    lenient()
        .when(organizationScopeResolver.resolveAccessibleOrganizationIds(any()))
        .thenReturn(Set.of());
    assertThat(policy.canView(null, user(100, "ROLE_USER", STORE_10, null))).isFalse();
  }
}
