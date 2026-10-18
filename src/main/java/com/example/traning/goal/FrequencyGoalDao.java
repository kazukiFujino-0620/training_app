package com.example.traning.goal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

/** 週の回数目標（user_frequency_goals）と週ごとの確定目標（user_frequency_week_targets）のDAO（機能見直し-1-#7）。 */
@Dao
@ConfigAutowireable
public interface FrequencyGoalDao {

  /**
   * 週ごとの確定目標の書き込みをユーザー単位で直列化するため、usersの該当行をロックする（トランザクション終了まで保持）。 同じユーザーの週を複数のリクエストが同時に
   * INSERTすると、InnoDBでは一意制約違反ではなくデッドロック（トランザクション全体のロールバック）になるため。
   */
  @Select
  Optional<Long> lockUserForWeekTargets(Long userId);

  @Select
  Optional<UserFrequencyGoal> selectGoalByUserId(Long userId);

  @Insert
  int insertGoal(UserFrequencyGoal entity);

  @Update(sqlFile = true)
  int updateGoalCount(Long userId, int weeklyTargetCount);

  /** 指定した週以前の確定目標（新しい週から順）。 */
  @Select
  List<UserFrequencyWeekTarget> selectWeekTargetsUntil(
      Long userId, LocalDate weekStartDate, int limit);

  @Select
  Optional<UserFrequencyWeekTarget> selectLatestWeekTarget(Long userId);

  @Insert
  int insertWeekTarget(UserFrequencyWeekTarget entity);

  /** 評価に含めない週（目標を設定した週）の目標値だけを置き換える。 */
  @Update(sqlFile = true)
  int updateUnevaluatedWeekTarget(Long userId, LocalDate weekStartDate, int targetCount);
}
