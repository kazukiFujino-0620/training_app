package com.example.traning.mobile.dao;

import com.example.traning.mobile.entity.MobileRefreshToken;
import java.time.LocalDateTime;
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
public interface MobileRefreshTokenDao {

  @Insert
  int insert(MobileRefreshToken token);

  @Select
  Optional<MobileRefreshToken> selectByTokenHash(String tokenHash);

  @Select
  List<MobileRefreshToken> selectActiveByDeviceId(String deviceId);

  @Update(sqlFile = true)
  int revokeByTokenHash(String tokenHash, LocalDateTime revokedAt);

  @Delete(sqlFile = true)
  int deleteByUserIdAndDeviceId(Long userId, String deviceId);

  @Delete(sqlFile = true)
  int deleteExpiredTokens(LocalDateTime now);

  /** ユーザーの全デバイス分のリフレッシュトークンを削除する（退会承認時に使用）。 */
  @Delete(sqlFile = true)
  int deleteByUserId(Long userId);

  /** データ保護期間（論理削除＋一定期間経過）を超えたユーザーに紐づくトークンを物理削除する。 */
  @Delete(sqlFile = true)
  int deleteExpiredPhysically(LocalDateTime cutoff);
}
