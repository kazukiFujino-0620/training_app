package com.example.traning.audit;

import java.time.LocalDate;
import java.util.List;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.boot.ConfigAutowireable;

@Dao
@ConfigAutowireable
public interface AuditLogDao {

  @Insert
  int insert(AuditLogEntry entry);

  @Select
  List<AuditLogEntry> selectForAdmin(
      Long userId, String action, LocalDate from, LocalDate to, int offset, int limit);

  @Select
  int countForAdmin(Long userId, String action, LocalDate from, LocalDate to);

  /**
   * ita1-1 フェーズ3: 組織スコープで絞り込む版（ROLE_ADMIN 以外用）。
   *
   * <p>Doma は List 型パラメータへの {@code null} 渡しを許容しない（DomaNullPointerException）ため、絞り込みなし（{@code
   * null}）の場合は {@link #selectForAdmin} を、絞り込みありの場合はこちらを使う。 organization_id が NULL
   * の行（組織不明な過去ログ）は結果から除外される。呼び出し側は空リストで呼び出さないこと（IN 句が空になるため、呼び出し前に空集合チェックをすること）。
   */
  @Select
  List<AuditLogEntry> selectForAdminByOrganizationIds(
      Long userId,
      String action,
      LocalDate from,
      LocalDate to,
      int offset,
      int limit,
      List<Long> organizationIds);

  /** ita1-1 フェーズ3: 組織スコープで絞り込む版（ROLE_ADMIN 以外用）。{@link #selectForAdminByOrganizationIds} 参照。 */
  @Select
  int countForAdminByOrganizationIds(
      Long userId, String action, LocalDate from, LocalDate to, List<Long> organizationIds);
}
