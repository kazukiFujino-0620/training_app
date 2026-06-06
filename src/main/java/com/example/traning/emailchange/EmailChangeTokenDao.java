package com.example.traning.emailchange;

import java.time.LocalDateTime;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface EmailChangeTokenDao {

    @Insert
    int insert(EmailChangeToken token);

    @Select
    Optional<EmailChangeToken> selectByToken(String token);

    @Delete(sqlFile = true)
    int deleteByUserId(Long userId);

    @Delete(sqlFile = true)
    int deleteExpiredTokens(LocalDateTime now);
}
