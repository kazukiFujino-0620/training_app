package com.example.traning.dao;

import com.example.traning.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;
import org.seasar.doma.jdbc.Result;

@Dao
@ConfigAutowireable
public interface UserDao {
  @Insert
  Result<User> insert(User user);

  @Select
  Optional<User> selectByEmail(String email);

  @Select
  List<User> selectAll();

  @Update
  Result<User> update(User user);

  @Select
  User selectById(Integer userId);

  @Select
  List<User> selectByName(String userName);

  @Update(sqlFile = true)
  int softDeleteById(Integer userId);

  @Delete(sqlFile = true)
  int deleteExpiredPhysically(LocalDateTime cutoff);

  @Select
  Optional<User> selectSoftDeletedByEmail(String email);

  @Update(sqlFile = true)
  int restoreById(Integer userId);

  @Update(sqlFile = true)
  Result<User> updateProfile(User user);

  @Update(sqlFile = true)
  int updateEmail(Long userId, String newEmail);
}
