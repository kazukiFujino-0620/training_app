package com.example.traning.dao.training;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.training.entity.TrainingDetail;

@Dao
@ConfigAutowireable
public interface TrainingDetailDao {

	@Insert
	int insert(TrainingDetail trainingDetail);

	@Update
	int update(TrainingDetail trainingDetail);

	@Delete(sqlFile = true)
	int deleteByTrainingId(Long trainingId);

	@Select
	List<TrainingDetail> selectByTrainingId(Long trainingId);
}
