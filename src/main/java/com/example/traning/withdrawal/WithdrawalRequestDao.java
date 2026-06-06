package com.example.traning.withdrawal;

import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface WithdrawalRequestDao {

  @Select
  Optional<WithdrawalRequest> selectPendingByUserId(Long userId);

  @Select
  List<WithdrawalRequest> selectAllPending();

  @Select
  Optional<WithdrawalRequest> selectById(Long id);

  @Insert
  int insert(WithdrawalRequest request);

  @Update
  int update(WithdrawalRequest request);
}
