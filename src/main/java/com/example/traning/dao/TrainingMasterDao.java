package com.example.traning.dao;

import java.util.List;

import org.seasar.doma.Insert;
import org.seasar.doma.Select;

import com.example.traning.entity.TrainingMaster;

public interface TrainingMasterDao {
	@Insert
	int insert(TrainingMaster trainingMaster);

	@Select
	List<TrainingMaster> selectAll();
}
