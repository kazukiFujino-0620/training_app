package com.example.traning.trainer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface TrainerAdviceDao {

  @Insert
  int insert(TrainerAdvice advice);

  @Update
  int update(TrainerAdvice advice);

  @Select
  Optional<TrainerAdvice> selectById(Long id);

  /** トレーニー向け: 指定ユーザー宛の削除済みを除くアドバイスを新しい順に返す。 */
  @Select
  List<TrainerAdvice> selectActiveByTargetUserId(Long targetUserId);

  /** トレーニー向け: 指定ユーザー宛・指定日が対象日のアドバイス（削除済み除く）を新しい順に返す。 */
  @Select
  List<TrainerAdvice> selectActiveByTargetUserIdAndDate(Long targetUserId, LocalDate targetDate);

  /** トレーナー向け: 自分が送信した削除済みを除くアドバイスを新しい順に返す。 */
  @Select
  List<TrainerAdvice> selectActiveByTrainerId(Long trainerId);
}
