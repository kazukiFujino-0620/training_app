package com.example.traning.organization;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface UserStoreAccessDao {

  @Insert
  int insert(UserStoreAccess userStoreAccess);

  @Select
  List<Long> selectStoreOrganizationIdsByUserId(Long userId);

  @Delete(sqlFile = true)
  int deleteByUserIdAndStoreOrganizationId(Long userId, Long storeOrganizationId);
}
