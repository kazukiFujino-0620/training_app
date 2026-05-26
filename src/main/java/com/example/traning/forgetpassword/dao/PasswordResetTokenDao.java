package com.example.traning.forgetpassword.dao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.forgetpassword.entity.PasswordResetToken;

@Dao
@ConfigAutowireable
public interface PasswordResetTokenDao {
    @Insert
    int insert(PasswordResetToken token);

    @Select
    PasswordResetToken selectByToken(String token);

    @Delete
    int delete(PasswordResetToken token);

    @Select
    Optional<PasswordResetToken> selectByUserId(Integer userId);

    @Delete(sqlFile = true)
    int deleteExpiredTokens(LocalDateTime now);

    /** 同一ユーザーの既存トークンをすべて削除する（新規発行前に呼び出す） */
    @Delete(sqlFile = true)
    int deleteByUserId(Integer userId);
}