package com.example.traning.dao;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.entity.Training;

@Dao
@ConfigAutowireable
public interface TrainingDao {
	@Insert
	int insert(Training training);

	@Select
	List<Training> selectAll();
}