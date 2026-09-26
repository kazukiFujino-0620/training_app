package com.example.traning.periodization;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Delete;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

/** トレーナーからの期分けプログラムの案のDAO。 */
@Dao
@ConfigAutowireable
public interface ProgramCycleProposalDao {

  @Select
  Optional<ProgramCycleProposal> selectById(Long id);

  /** トレーニー宛てで、まだ選択していない案（PENDING、新しい順）。 */
  @Select
  List<ProgramCycleProposal> selectPendingByTrainee(Long traineeUserId);

  /** トレーニーの予約中の案（SCHEDULED、古い順）。 */
  @Select
  List<ProgramCycleProposal> selectScheduledByTrainee(Long traineeUserId);

  /** トレーナーが送った案（新しい順）。 */
  @Select
  List<ProgramCycleProposal> selectByTrainer(Long trainerUserId);

  @Insert
  int insert(ProgramCycleProposal entity);

  @Update(sqlFile = true)
  int markResponded(Long id, String status, LocalDateTime respondedAt);

  /** トレーニー宛ての返事待ち（PENDING）の案をすべて「新しい案に置き換え」（SUPERSEDED）にする。 */
  @Update(sqlFile = true)
  int supersedePendingByTrainee(Long traineeUserId);

  /** 状態のみを変更する（取り下げ等）。 */
  @Update(sqlFile = true)
  int updateStatus(Long id, String status);

  /** 名前・週数と、送信後に編集した日時（送信時はnull）を更新する。 */
  @Update(sqlFile = true)
  int updateContentMeta(Long id, String name, int totalWeeks, LocalDateTime contentUpdatedAt);

  @Select
  List<ProgramCycleProposalWeek> selectWeeksByProposalId(Long proposalId);

  @Select
  List<ProgramCycleProposalDayTemplate> selectDayTemplatesByProposalId(Long proposalId);

  /** 案配下の全曜日にぶら下がる種目（day_template_id・display_order順）。 */
  @Select
  List<ProgramCycleProposalDayTemplateItem> selectItemsByProposalId(Long proposalId);

  @Insert
  int insertWeek(ProgramCycleProposalWeek entity);

  @Insert
  int insertDayTemplate(ProgramCycleProposalDayTemplate entity);

  @Insert
  int insertDayTemplateItem(ProgramCycleProposalDayTemplateItem entity);

  @Delete(sqlFile = true)
  int deleteItemsByProposalId(Long proposalId);

  @Delete(sqlFile = true)
  int deleteDayTemplatesByProposalId(Long proposalId);

  @Delete(sqlFile = true)
  int deleteWeeksByProposalId(Long proposalId);

  @Update(sqlFile = true)
  int markStarted(Long id, Long startedCycleId, LocalDateTime startedAt);
}
