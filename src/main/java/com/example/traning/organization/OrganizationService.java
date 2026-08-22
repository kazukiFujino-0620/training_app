package com.example.traning.organization;

import com.example.traning.dao.UserDao;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 組織・店舗の作成/編集/一覧取得を、操作者の権限に応じて制御するサービス（ita1-1 未実施分）。
 *
 * <ul>
 *   <li>GYM（組織）の新規作成: ROLE_ADMINのみ
 *   <li>STORE（店舗）の新規作成: ROLE_ADMIN、およびROLE_ORG_ADMINは自組織配下に限り
 *   <li>編集: 作成権限と同じ範囲
 * </ul>
 */
@Service
public class OrganizationService {

  private final OrganizationDao organizationDao;
  private final UserDao userDao;

  public OrganizationService(OrganizationDao organizationDao, UserDao userDao) {
    this.organizationDao = organizationDao;
    this.userDao = userDao;
  }

  /** 組織ツリー表示用ノード。GYMは配下のSTOREをchildrenに持つ。 */
  public record OrganizationNode(
      Long id, String name, String type, long userCount, List<OrganizationNode> children) {}

  /** 操作者の権限に応じたGYM＋配下STOREのツリーを返す。STORE_ADMIN以下はこの画面自体にアクセスできない前提。 */
  public List<OrganizationNode> getOrganizationTree(User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    List<Organization> gyms;
    if (role == Role.ADMIN) {
      gyms =
          organizationDao.selectAll().stream()
              .filter(o -> OrganizationType.GYM.name().equals(o.getType()))
              .collect(Collectors.toList());
    } else if (role == Role.ORG_ADMIN) {
      Organization own =
          organizationDao
              .selectById(currentAdmin.getOrganizationId())
              .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
      gyms = List.of(own);
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この画面へのアクセス権限がありません");
    }

    Map<Long, Long> userCountByOrganization =
        userDao.selectAll().stream()
            .filter(u -> u.getOrganizationId() != null)
            .collect(Collectors.groupingBy(User::getOrganizationId, Collectors.counting()));

    return gyms.stream()
        .map(gym -> toNode(gym, userCountByOrganization))
        .collect(Collectors.toList());
  }

  private OrganizationNode toNode(Organization gym, Map<Long, Long> userCountByOrganization) {
    List<OrganizationNode> children =
        organizationDao.selectByParentOrganizationId(gym.getId()).stream()
            .map(
                store ->
                    new OrganizationNode(
                        store.getId(),
                        store.getName(),
                        store.getType(),
                        userCountByOrganization.getOrDefault(store.getId(), 0L),
                        List.of()))
            .collect(Collectors.toList());
    return new OrganizationNode(
        gym.getId(),
        gym.getName(),
        gym.getType(),
        userCountByOrganization.getOrDefault(gym.getId(), 0L),
        children);
  }

  @Transactional
  public Organization createGym(String name, User currentAdmin) {
    if (Role.fromValue(currentAdmin.getRole()) != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "組織の新規作成はシステム管理者のみ行えます");
    }
    Organization gym = new Organization();
    gym.setName(name);
    gym.setType(OrganizationType.GYM.name());
    organizationDao.insert(gym);
    return gym;
  }

  @Transactional
  public Organization createStore(String name, Long parentGymId, User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role == Role.ADMIN) {
      // 任意のGYM配下に作成可
    } else if (role == Role.ORG_ADMIN) {
      if (!parentGymId.equals(currentAdmin.getOrganizationId())) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "自組織配下にのみ店舗を登録できます");
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "店舗の新規登録権限がありません");
    }
    Organization gym =
        organizationDao
            .selectById(parentGymId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定された組織が存在しません"));
    if (!OrganizationType.GYM.name().equals(gym.getType())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "指定された組織はGYMではありません");
    }
    Organization store = new Organization();
    store.setName(name);
    store.setType(OrganizationType.STORE.name());
    store.setParentOrganizationId(parentGymId);
    organizationDao.insert(store);
    return store;
  }

  @Transactional
  public void renameOrganization(Long id, String newName, User currentAdmin) {
    Organization target =
        organizationDao
            .selectById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "指定された組織が存在しません"));
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role == Role.ADMIN) {
      // 任意の組織を編集可
    } else if (role == Role.ORG_ADMIN) {
      boolean isOwnGym = target.getId().equals(currentAdmin.getOrganizationId());
      boolean isOwnStore =
          currentAdmin.getOrganizationId().equals(target.getParentOrganizationId());
      if (!isOwnGym && !isOwnStore) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "自組織配下の組織・店舗のみ編集できます");
      }
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "編集権限がありません");
    }
    target.setName(newName);
    organizationDao.update(target);
  }
}
