package com.example.traning.organization;

import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface OrganizationDao {

  @Insert
  int insert(Organization organization);

  @Select
  Optional<Organization> selectById(Long id);

  @Select
  List<Organization> selectByParentOrganizationId(Long parentOrganizationId);

  /** お知らせ配信先選択（ita2-5）等、管理者が全組織から選ぶ用途で使用。 */
  @Select
  List<Organization> selectAll();

  /** お知らせ配信先選択（ita2-5）等、アクセス可能な組織集合に絞って選ぶ用途で使用。 */
  @Select
  List<Organization> selectByIds(List<Long> ids);
}
