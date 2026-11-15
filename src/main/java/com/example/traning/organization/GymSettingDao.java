package com.example.traning.organization;

import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface GymSettingDao {

  @Select
  Optional<GymSetting> selectByOrganizationId(Long organizationId);

  @Insert
  int insert(GymSetting gymSetting);

  @Update
  int update(GymSetting gymSetting);
}
