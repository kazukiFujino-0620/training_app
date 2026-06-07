package com.example.traning.template;

import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TrainingTemplateDao {

  @Select
  List<TrainingTemplate> selectByUserId(Long userId);

  @Select
  Optional<TrainingTemplate> selectById(Long id);

  @Insert
  int insert(TrainingTemplate template);

  @Update
  int update(TrainingTemplate template);

  @Update(sqlFile = true)
  int softDeleteById(Long id);
}
