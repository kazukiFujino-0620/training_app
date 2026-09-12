package com.example.traning.organization;

import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface InviteCodeDao {

  @Insert
  int insert(InviteCode inviteCode);

  @Update
  int update(InviteCode inviteCode);

  @Select
  Optional<InviteCode> selectById(Long id);

  @Select
  List<InviteCode> selectAll();

  @Select
  List<InviteCode> selectByOrganizationIds(List<Long> organizationIds);

  /** トレーナー登録（ita4-3）での引換用。コードは一意（UNIQUE制約）。 */
  @Select
  Optional<InviteCode> selectByCode(String code);
}
