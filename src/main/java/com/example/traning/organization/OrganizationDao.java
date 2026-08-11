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
}
