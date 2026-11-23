package com.example.traning.goal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

/** 目標の提案（goal_proposals）と会員が自分で変えた記録（member_goal_changes）のDAO（機能見直し-1-#20）。 */
@Dao
@ConfigAutowireable
public interface GoalProposalDao {

  @Insert
  int insertProposal(GoalProposal entity);

  /** 提案した人の名前付き。 */
  @Select
  Optional<GoalProposal> selectProposalById(Long id);

  /** 会員の提案（提案した人の名前付き、新しい順）。 */
  @Select
  List<GoalProposal> selectProposalsByMember(Long memberUserId);

  /** 会員ごとの最新の提案（担当会員の一覧の見直し日）。 */
  @Select
  List<GoalProposal> selectLatestProposalsByMemberIds(List<Long> memberIds);

  /** 返事待ちの提案を取り下げにする（新しい提案を送る直前）。 */
  @Update(sqlFile = true)
  int withdrawPendingByMember(Long memberUserId, LocalDateTime now);

  /** 返事待ちの提案に会員の返事を記録する。返事待ちでなければ更新しない（0件）。 */
  @Update(sqlFile = true)
  int markResponded(Long id, String status, LocalDateTime now);

  @Insert
  int insertChange(MemberGoalChange entity);

  /** 会員が自分で変えた記録（新しい順）。 */
  @Select
  List<MemberGoalChange> selectChangesByMember(Long memberUserId);
}
