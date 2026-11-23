package com.example.traning.follow;

import com.example.traning.smarttrainer.autogen.UserExcludedItem;
import com.example.traning.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

/** 担当会員の一覧と「フォロー推奨」の印（機能見直し-1-#12）のDAO。 */
@Dao
@ConfigAutowireable
public interface MemberFollowDao {

  /** 担当トレーナーが{@code trainerId}の会員（ROLE_USER、削除済みを除く）。 */
  @Select
  List<User> selectMembersByAssignedTrainerId(Long trainerId);

  /** 組織（店舗・ジム）に所属する会員（ROLE_USER、削除済みを除く）。 */
  @Select
  List<User> selectMembersByOrganizationIds(List<Long> organizationIds);

  /** 担当トレーナーの名前の表示用（削除済みのスタッフも名前を出す）。 */
  @Select
  List<User> selectUsersByIds(List<Long> userIds);

  /** 痛みの報告として扱う除外（理由が入力されていて、{@code since}以降に追加したもの）。 */
  @Select
  List<UserExcludedItem> selectReasonedExclusionsSince(List<Long> userIds, LocalDateTime since);

  /** 会員ごとの最新の「対応済み」（押した人の名前付き）。 */
  @Select
  List<MemberFollowAcknowledgement> selectLatestAcknowledgementsByMemberIds(List<Long> memberIds);

  @Insert
  int insertAcknowledgement(MemberFollowAcknowledgement entity);
}
