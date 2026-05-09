package com.example.traning.training.dao;

import java.time.LocalDate;
import java.util.List;

import org.seasar.doma.Column;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.training.Training;
import com.example.traning.user.User;

@Dao
@ConfigAutowireable
public interface TrainingDao {

	@Select
	List<Training> selectByDate(Long userId, LocalDate startDate, LocalDate endDate);

	@Select
	Training selectById(Long id);

	@Select
	Long selectIdByUsername(String username);

	@Insert
	int insert(Training training);

	@Update
	int update(Training training);

	@Delete
	int delete(Training training);

	@Select
	List<Training> selectByUserIdAndDateRange(Integer userId, LocalDate startDate, LocalDate endDate);

	@Select
	User selectByUserName(Long userId);

	@Select
	List<VolumeResult> selectVolumeList(Long userId, String partCode, String startDate, String endDate);

	@org.seasar.doma.Entity
	public static class VolumeResult {
		@Column(name = "training_date")
		public String trainingDate;

		@Column(name = "total_volume")
		public Double totalVolume;
	}
}