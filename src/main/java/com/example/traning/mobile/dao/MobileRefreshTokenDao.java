package com.example.traning.mobile.dao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

import com.example.traning.mobile.entity.MobileRefreshToken;

@Dao
@ConfigAutowireable
public interface MobileRefreshTokenDao {

	@Insert
	int insert(MobileRefreshToken token);

	@Select
	Optional<MobileRefreshToken> selectByTokenHash(String tokenHash);

	@Update(sqlFile = true)
	int revokeByTokenHash(String tokenHash, LocalDateTime revokedAt);

	@Delete(sqlFile = true)
	int deleteByUserIdAndDeviceId(Long userId, String deviceId);

	@Delete(sqlFile = true)
	int deleteExpiredTokens(LocalDateTime now);
}
