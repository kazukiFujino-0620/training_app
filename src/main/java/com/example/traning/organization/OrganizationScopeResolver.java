package com.example.traning.organization;

import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ログインユーザーのロール・所属組織から「アクセス可能な organization_id 集合」を解決するコンポーネント（ita1-1 マルチテナント化 フェーズ3）。
 *
 * <ul>
 *   <li>ROLE_ADMIN: {@code null}（フィルタ不要＝全組織アクセス可）
 *   <li>ROLE_ORG_ADMIN: 自組織自身 + 自組織（GYM）配下の全店舗
 *   <li>ROLE_STORE_ADMIN: 自店舗自身 + {@code user_store_access} に登録された兼任店舗
 *   <li>ROLE_USER: 自分の所属組織のみ
 * </ul>
 *
 * <p>{@link #resolveAccessibleOrganizationIds(User)} が返す {@code null} は「フィルタ不要（全組織アクセス可）」を意味し、
 * 空集合はスコープが解決できない（=アクセス可能な組織が無い）ことを意味する。呼び出し側はこの2つを明確に区別すること。
 */
@Component
public class OrganizationScopeResolver {

  private final OrganizationDao organizationDao;
  private final UserStoreAccessDao userStoreAccessDao;

  public OrganizationScopeResolver(
      OrganizationDao organizationDao, UserStoreAccessDao userStoreAccessDao) {
    this.organizationDao = organizationDao;
    this.userStoreAccessDao = userStoreAccessDao;
  }

  /**
   * アクセス可能な organization_id の集合を返す。
   *
   * @return {@code null} の場合はフィルタ不要（ROLE_ADMIN のみ）。それ以外は明示的な集合（空集合の場合もあり得る）。
   */
  public Set<Long> resolveAccessibleOrganizationIds(User user) {
    if (user == null || user.getRole() == null) {
      return Set.of();
    }

    Role role = Role.fromValue(user.getRole());
    if (role == Role.ADMIN) {
      return null;
    }

    Long ownOrganizationId = user.getOrganizationId();
    if (ownOrganizationId == null) {
      return Set.of();
    }

    if (role == Role.ORG_ADMIN) {
      Set<Long> ids = new HashSet<>();
      ids.add(ownOrganizationId);
      List<Organization> stores = organizationDao.selectByParentOrganizationId(ownOrganizationId);
      for (Organization store : stores) {
        ids.add(store.getId());
      }
      return ids;
    }

    if (role == Role.STORE_ADMIN) {
      Set<Long> ids = new HashSet<>();
      ids.add(ownOrganizationId);
      List<Long> concurrentStoreIds =
          userStoreAccessDao.selectStoreOrganizationIdsByUserId(user.getUserId().longValue());
      ids.addAll(concurrentStoreIds);
      return ids;
    }

    // ROLE_USER（および未知ロール）は自分の所属組織のみアクセス可能とする（最小権限）。
    return Set.of(ownOrganizationId);
  }

  /** 対象 organization_id へのアクセス可否を判定する。 */
  public boolean canAccessOrganization(User user, Long targetOrganizationId) {
    Set<Long> accessibleOrganizationIds = resolveAccessibleOrganizationIds(user);
    if (accessibleOrganizationIds == null) {
      return true; // ROLE_ADMIN: 全組織アクセス可
    }
    if (targetOrganizationId == null) {
      return false; // 組織不明のデータは非ADMINからはアクセス不可とする
    }
    return accessibleOrganizationIds.contains(targetOrganizationId);
  }
}
