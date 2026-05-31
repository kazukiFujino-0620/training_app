package com.example.traning.training.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.seasar.doma.Column;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.training.Training;
import com.example.traning.training.dao.TrainingDao.VolumeResult;
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

	@Select
	Long selectIdByEmail(String email);

	@Insert
	int insert(Training training);

	@Update
	int update(Training training);

	@Delete
	int delete(Training training);

	@Update(sqlFile = true)
	int softDeleteById(Long id);

	@Delete(sqlFile = true)
	int deleteExpiredPhysically(LocalDateTime cutoff);

	@Select
	List<Training> selectByUserIdAndDateRange(Integer userId, LocalDate startDate, LocalDate endDate);

	@Select
	List<Training> selectByUserIdAndDate(Integer userId, LocalDate date);

	@Select
	User selectByUserName(String userName);

	@Select
	User selectByEmail(String email);

	@Select
	List<VolumeResult> selectVolumeList(Long userId, String partCode, String startDate, String endDate);

	@Select
	List<Training> selectRecentSessionsByItem(Long userId, String itemName, LocalDate before, int limit);

	@org.seasar.doma.Entity
	public static class VolumeResult {
		@Column(name = "training_date")
		public String trainingDate;

		@Column(name = "total_volume")
		public Double totalVolume;
	}
}