package com.example.traning.goal;

import java.util.List;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface GoalDao {

    @Select
    List<TrainingGoal> selectByUserId(Long userId);

    @Select
    Optional<TrainingGoal> selectById(Long id);

    @Insert
    int insert(TrainingGoal goal);

    @Update
    int update(TrainingGoal goal);

    @Delete
    int delete(TrainingGoal goal);
}
