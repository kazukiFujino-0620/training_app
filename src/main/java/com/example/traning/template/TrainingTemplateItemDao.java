package com.example.traning.template;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TrainingTemplateItemDao {

    @Select
    List<TrainingTemplateItem> selectByTemplateId(Long templateId);

    @Insert
    int insert(TrainingTemplateItem item);

    @Delete(sqlFile = true)
    int deleteByTemplateId(Long templateId);
}
