package com.example.traning.notice;

import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.Update;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface NoticeDao {

  /** 指定組織向けの、指定ユーザーがまだ閲覧（dismiss）していないお知らせを新しい順に返す。 */
  @Select
  List<Notice> selectActiveByOrganizationIdForUser(Long organizationId, Long userId);

  /** 管理画面用: 指定組織のお知らせ一覧（削除済み除く）を新しい順に返す。 */
  @Select
  List<Notice> selectByOrganizationId(Long organizationId);

  @Select
  Notice selectById(Long id);

  @Insert
  int insert(Notice notice);

  @Update
  int update(Notice notice);

  @Insert(sqlFile = true)
  int insertDismissal(Long noticeId, Long userId);
}
