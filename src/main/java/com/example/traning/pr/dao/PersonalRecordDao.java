package com.example.traning.pr.dao;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.pr.PersonalRecord;

@Dao
@ConfigAutowireable
public interface PersonalRecordDao {

    @Select
    List<PersonalRecord> selectByUserId(Long userId);

    @Select
    Optional<PersonalRecord> selectByUserIdAndItem(Long userId, String itemName);

    @Insert
    int insert(PersonalRecord personalRecord);

    @Update
    int update(PersonalRecord personalRecord);
}
