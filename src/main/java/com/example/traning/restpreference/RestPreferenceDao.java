package com.example.traning.restpreference;

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
public interface RestPreferenceDao {

  @Select
  List<UserItemRestPreference> selectByUserId(Long userId);

  @Select
  Optional<UserItemRestPreference> selectByUserIdAndItemName(Long userId, String itemName);

  @Insert
  int insert(UserItemRestPreference pref);

  @Update
  int update(UserItemRestPreference pref);

  @Delete
  int delete(UserItemRestPreference pref);
}
