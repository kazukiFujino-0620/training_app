package com.example.traning.dao;

import java.util.List;

import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.entity.TrainingItemMaster;
import com.example.traning.entity.TrainingMaster;

@Dao
@ConfigAutowireable
public interface TrainingMasterDao {
	@Insert
	int insert(TrainingMaster trainingMaster);

	@Select
	List<TrainingMaster> selectAllParts();

	@Select
	List<TrainingItemMaster> selectItemsByPart(String partCode);

	@Select
	String selectNameByCode(String partCode);
}
