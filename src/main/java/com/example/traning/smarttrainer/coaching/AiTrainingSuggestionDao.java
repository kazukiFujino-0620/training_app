package com.example.traning.smarttrainer.coaching;

import java.time.LocalDate;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AiTrainingSuggestionDao {

  @Select
  Optional<AiTrainingSuggestion> selectByUserIdAndDate(Long userId, LocalDate targetDate);

  @Insert
  int insert(AiTrainingSuggestion suggestion);
}
