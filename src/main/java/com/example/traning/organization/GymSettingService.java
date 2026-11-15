package com.example.traning.organization;

import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * ジムごとの設定の読み書き（機能見直し-1-#15 Q15-2）。会員・店舗の organization_id から所属ジムを解決して設定を引く。
 *
 * <p>設定を変えられるのはジム管理者（自分のジムのみ）とシステム管理者。店舗ごとの上書きはしない。
 */
@Service
public class GymSettingService {

  private final GymSettingDao gymSettingDao;
  private final OrganizationDao organizationDao;

  public GymSettingService(GymSettingDao gymSettingDao, OrganizationDao organizationDao) {
    this.gymSettingDao = gymSettingDao;
    this.organizationDao = organizationDao;
  }

  /** 組織（店舗またはジム）が属するジムの organization_id を返す。店舗なら親のジム、ジムならそれ自身。 組織が見つからない・全組織共通（ALL）の場合は空。 */
  public Optional<Long> resolveGymId(Long organizationId) {
    if (organizationId == null) {
      return Optional.empty();
    }
    return organizationDao
        .selectById(organizationId)
        .flatMap(
            org -> {
              if (OrganizationType.GYM.name().equals(org.getType())) {
                return Optional.of(org.getId());
              }
              if (OrganizationType.STORE.name().equals(org.getType())) {
                return Optional.ofNullable(org.getParentOrganizationId());
              }
              return Optional.empty();
            });
  }

  /** ジムの設定。行が無い場合は初期値（全項目が控えめな側）の設定を返す（保存はしない）。 */
  public GymSetting getForGym(Long gymId) {
    return gymSettingDao
        .selectByOrganizationId(gymId)
        .orElseGet(
            () -> {
              GymSetting defaults = new GymSetting();
              defaults.setOrganizationId(gymId);
              return defaults;
            });
  }

  /** 組織（店舗またはジム）が属するジムで、店舗管理者・ジム管理者が会員の健康に関わる情報を見られるか。不明な場合は見られない。 */
  public boolean isStaffHealthInfoVisible(Long organizationId) {
    return resolveGymId(organizationId)
        .map(gymId -> Boolean.TRUE.equals(getForGym(gymId).getStaffHealthInfoVisible()))
        .orElse(false);
  }

  /** 操作者が設定を変えられるジムの一覧。ジム管理者は自分のジムのみ、システム管理者は全ジム。 */
  public List<Organization> listEditableGyms(User actor) {
    Role role = Role.fromValue(actor.getRole());
    if (role == Role.ADMIN) {
      return organizationDao.selectAll().stream()
          .filter(o -> OrganizationType.GYM.name().equals(o.getType()))
          .toList();
    }
    if (role == Role.ORG_ADMIN) {
      return organizationDao
          .selectById(actor.getOrganizationId())
          .filter(o -> OrganizationType.GYM.name().equals(o.getType()))
          .map(List::of)
          .orElse(List.of());
    }
    return List.of();
  }

  @Transactional
  public void updateStaffHealthInfoVisible(User actor, Long gymId, boolean visible) {
    boolean editable = listEditableGyms(actor).stream().anyMatch(g -> g.getId().equals(gymId));
    if (!editable) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "このジムの設定を変更する権限がありません");
    }
    Optional<GymSetting> existing = gymSettingDao.selectByOrganizationId(gymId);
    GymSetting setting = existing.orElseGet(GymSetting::new);
    setting.setOrganizationId(gymId);
    setting.setStaffHealthInfoVisible(visible);
    setting.setUpdatedBy(actor.getUserId().longValue());
    if (existing.isPresent()) {
      gymSettingDao.update(setting);
    } else {
      gymSettingDao.insert(setting);
    }
  }
}
