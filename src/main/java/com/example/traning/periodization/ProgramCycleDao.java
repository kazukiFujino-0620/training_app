package com.example.traning.periodization;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

/** 期分けサイクル関連テーブルのDAO（機能見直し-1-#3）。 */
@Dao
@ConfigAutowireable
public interface ProgramCycleDao {

  @Select
  Optional<ProgramCycle> selectActiveByUserId(Long userId);

  @Select
  Optional<ProgramCycle> selectById(Long id);

  /** 直近に作成したサイクル（id降順）。Q3-7: サイクル終了後の「同じ内容で継続」で直近サイクルを参照する。 */
  @Select
  List<ProgramCycle> selectRecentByUserId(Long userId, int limit);

  /** 期間 [startDate, endDate] とサイクル期間（start_date〜start_date+total_weeks*7日未満）が重なるサイクル。 */
  @Select
  List<ProgramCycle> selectOverlappingByUserId(Long userId, LocalDate startDate, LocalDate endDate);

  @Select
  List<ProgramCycleWeek> selectWeeksByCycleId(Long cycleId);

  @Select
  List<ProgramCycleDayTemplate> selectDayTemplatesByCycleId(Long cycleId);

  @Select
  Optional<ProgramCycleDayTemplate> selectDayTemplateById(Long id);

  @Select
  Optional<ProgramCycleDayTemplate> selectDayTemplateByCycleWeekDay(
      Long cycleId, int weekNumber, String dayOfWeek);

  /** サイクル配下の全曜日テンプレートにぶら下がる種目（day_template_id・display_order順）。 */
  @Select
  List<ProgramCycleDayTemplateItem> selectItemsByCycleId(Long cycleId);

  @Select
  List<ProgramCycleDayTemplateItem> selectItemsByDayTemplateId(Long dayTemplateId);

  @Select
  List<ProgramCycleItemBaseline> selectBaselinesByCycleId(Long cycleId);

  @Insert
  int insert(ProgramCycle entity);

  @Insert
  int insertWeek(ProgramCycleWeek entity);

  @Insert
  int insertDayTemplate(ProgramCycleDayTemplate entity);

  @Insert
  int insertDayTemplateItem(ProgramCycleDayTemplateItem entity);

  @Insert
  int insertItemBaseline(ProgramCycleItemBaseline entity);

  @Delete(sqlFile = true)
  int deleteItemsByDayTemplateId(Long dayTemplateId);

  /** statusをARCHIVEDにする（新サイクル採用時に旧ACTIVEサイクルを退避）。 */
  @Update(sqlFile = true)
  int archiveById(Long id);

  /** statusをCOMPLETEDにする（total_weeks経過時。Q3-7）。 */
  @Update(sqlFile = true)
  int completeById(Long id);

  /** サイクル終了後の次の行き先を選んだ日時を記録する（未記録の場合のみ。QA Q3-7追記）。 */
  @Update(sqlFile = true)
  int markRenewDecidedById(Long id, LocalDateTime decidedAt);

  @Update(sqlFile = true)
  int updateDayTemplatePartCode(Long id, String partCode);
}
