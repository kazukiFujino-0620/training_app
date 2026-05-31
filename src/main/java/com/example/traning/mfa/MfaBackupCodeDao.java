package com.example.traning.mfa;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface MfaBackupCodeDao {

    @Select
    List<MfaBackupCode> selectByUserId(Long userId);

    @Insert
    int insert(MfaBackupCode code);

    @Update
    int update(MfaBackupCode code);

    @Update(sqlFile = true)
    int deleteByUserId(Long userId);
}
