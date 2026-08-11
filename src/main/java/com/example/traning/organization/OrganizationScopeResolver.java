package com.example.traning.organization;

import com.example.traning.user.User;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ログインユーザーのロール・所属組織から、アクセス可能な organization_id の集合を解決する。
 *
 * <p>{@link #resolveAccessibleOrganizationIds(User)} が {@code null} を返す場合は
 * 「全組織アクセス可」（ROLE_ADMIN）を意味する。呼び出し側は null を「フィルタ不要」として扱うこと。
 */
@Component
public class OrganizationScopeResolver {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";
  private static final String ROLE_ORG_ADMIN = "ROLE_ORG_ADMIN";
  private static final String ROLE_STORE_ADMIN = "ROLE_STORE_ADMIN";

  private final OrganizationDao organizationDao;
  private final UserStoreAccessDao userStoreAccessDao;

  public OrganizationScopeResolver(
      OrganizationDao organizationDao, UserStoreAccessDao userStoreAccessDao) {
    this.organizationDao = organizationDao;
    this.userStoreAccessDao = userStoreAccessDao;
  }

  /**
   * @return アクセス可能な organization_id 一覧。ROLE_ADMIN の場合は null（全組織アクセス可＝フィルタ不要）。
   */
  public List<Long> resolveAccessibleOrganizationIds(User user) {
    String role = user.getRole();

    if (ROLE_ADMIN.equals(role)) {
      return null;
    }

    if (ROLE_ORG_ADMIN.equals(role)) {
      List<Long> ids = new ArrayList<>();
      ids.add(user.getOrganizationId());
      organizationDao
          .selectByParentOrganizationId(user.getOrganizationId())
          .forEach(org -> ids.add(org.getId()));
      return ids;
    }

    if (ROLE_STORE_ADMIN.equals(role)) {
      List<Long> ids = new ArrayList<>();
      ids.add(user.getOrganizationId());
      ids.addAll(
          userStoreAccessDao.selectStoreOrganizationIdsByUserId(user.getUserId().longValue()));
      return ids;
    }

    // ROLE_USER: 自分の所属組織のみ
    return List.of(user.getOrganizationId());
  }

  /** 対象データの organization_id にユーザーがアクセス可能かを判定する（IDOR対策）。 */
  public boolean canAccessOrganization(User user, Long targetOrganizationId) {
    List<Long> accessible = resolveAccessibleOrganizationIds(user);
    return accessible == null || accessible.contains(targetOrganizationId);
  }
}
