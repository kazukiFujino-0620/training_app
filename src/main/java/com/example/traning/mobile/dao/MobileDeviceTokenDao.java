package com.example.traning.mobile.dao;

import com.example.traning.mobile.entity.MobileDeviceToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

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

  /** ユーザーの全デバイス分のプッシュ通知トークンを削除する（退会承認時に使用）。 */
  @Delete(sqlFile = true)
  int deleteByUserId(Long userId);

  /** データ保護期間（論理削除＋一定期間経過）を超えたユーザーに紐づくトークンを物理削除する。 */
  @Delete(sqlFile = true)
  int deleteExpiredPhysically(LocalDateTime cutoff);
}
