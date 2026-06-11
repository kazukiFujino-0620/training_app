package com.example.traning.user.dao;

import com.example.traning.user.AccountRestoreToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AccountRestoreTokenDao {

  @Insert
  int insert(AccountRestoreToken token);

  @Select
  Optional<AccountRestoreToken> selectByToken(String token);

  @Delete(sqlFile = true)
  int deleteByUserId(Integer userId);

  @Delete(sqlFile = true)
  int deleteExpiredTokens(LocalDateTime now);
}
