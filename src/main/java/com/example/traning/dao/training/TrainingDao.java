package com.example.traning.dao.training;

import java.time.LocalDate;
import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.training.entity.Training;

@Dao
@ConfigAutowireable
public interface TrainingDao {

	@Select
	List<Training> selectByDate(Long userId, LocalDate date);

	@Select
	Training selectById(Long id);

	@Insert
	int insert(Training training);

	@Update
	int update(Training training);

	@Delete
	int delete(Training training);
}