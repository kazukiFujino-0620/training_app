package com.example.traning.mfa;

import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface MfaSettingDao {

  @Select
  Optional<UserMfaSetting> selectByUserId(Long userId);

  @Insert
  int insert(UserMfaSetting setting);

  @Update
  int update(UserMfaSetting setting);

  @Delete
  int delete(UserMfaSetting setting);

  @Update(sqlFile = true)
  int deleteByUserId(Long userId);
}
