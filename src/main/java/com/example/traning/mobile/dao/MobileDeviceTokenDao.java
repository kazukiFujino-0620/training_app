package com.example.traning.mobile.dao;

import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.mobile.entity.MobileDeviceToken;

@Dao
@ConfigAutowireable
public interface MobileDeviceTokenDao {

	@Select
	Optional<MobileDeviceToken> selectByUserIdAndDeviceId(Long userId, String deviceId);

	@Insert
	int insert(MobileDeviceToken token);

	@Update
	int update(MobileDeviceToken token);

	@Delete(sqlFile = true)
	int deleteByUserIdAndDeviceId(Long userId, String deviceId);
}
