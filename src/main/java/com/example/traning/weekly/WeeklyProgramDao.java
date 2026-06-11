package com.example.traning.weekly;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface WeeklyProgramDao {

  @Select
  List<WeeklyProgram> selectByUserId(Long userId);

  @Insert
  int insert(WeeklyProgram program);

  @Delete(sqlFile = true)
  int deleteByUserId(Long userId);
}
