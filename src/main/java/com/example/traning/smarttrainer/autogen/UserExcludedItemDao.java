package com.example.traning.smarttrainer.autogen;

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
public interface UserExcludedItemDao {

  @Select
  List<UserExcludedItem> selectByUserId(Long userId);

  @Select
  Optional<UserExcludedItem> selectByUserIdAndItemName(Long userId, String itemName);

  @Insert
  int insert(UserExcludedItem item);

  @Update
  int update(UserExcludedItem item);

  @Delete
  int delete(UserExcludedItem item);
}
