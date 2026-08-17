package com.example.traning.notice;

import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.User;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

  private final NoticeDao noticeDao;
  private final OrganizationScopeResolver organizationScopeResolver;

  /** ログインユーザーがまだ閲覧していない、自分の所属組織向けのお知らせを新しい順に返す。 */
  public List<Notice> getActiveForUser(User user) {
    if (user == null || user.getOrganizationId() == null) {
      return List.of();
    }
    return noticeDao.selectActiveByOrganizationIdForUser(
        user.getOrganizationId(), user.getUserId().longValue());
  }

  /**
   * お知らせを新規作成する。作成者のロール・所属組織から、指定した organizationId への配信権限があるかを検証する。
   *
   * @throws IllegalArgumentException 権限がない組織を指定した場合、または入力値が不正な場合
   */
  @Transactional
  public Notice create(User creator, Long organizationId, String title, String body) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("タイトルを入力してください");
    }
    if (title.length() > 200) {
      throw new IllegalArgumentException("タイトルは200文字以内で入力してください");
    }
    if (body == null || body.isBlank()) {
      throw new IllegalArgumentException("本文を入力してください");
    }
    if (organizationId == null) {
      throw new IllegalArgumentException("配信先を選択してください");
    }

    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(creator);
    if (accessible != null && !accessible.contains(organizationId)) {
      throw new IllegalArgumentException("この組織へのお知らせ配信権限がありません");
    }

    Notice notice = new Notice();
    notice.setOrganizationId(organizationId);
    notice.setTitle(title.trim());
    notice.setBody(body.trim());
    notice.setCreatedBy(creator.getUserId().longValue());
    noticeDao.insert(notice);
    return notice;
  }

  /** 管理画面用: 指定組織のお知らせ一覧（削除済み除く）を新しい順に返す。creatorの配信権限も検証する。 */
  public List<Notice> listForAdmin(User admin, Long organizationId) {
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(admin);
    if (accessible != null && !accessible.contains(organizationId)) {
      throw new IllegalArgumentException("この組織のお知らせを閲覧する権限がありません");
    }
    return noticeDao.selectByOrganizationId(organizationId);
  }

  /** 指定ユーザーに対して、このお知らせを以後表示しないようにする（既読管理の代わりに閲覧＝非表示）。 */
  @Transactional
  public void dismiss(Long noticeId, Long userId) {
    noticeDao.insertDismissal(noticeId, userId);
  }

  /** 管理画面用: 誤って投稿したお知らせを取り下げる（論理削除、全ユーザーから非表示になる）。 */
  @Transactional
  public void delete(User admin, Long noticeId) {
    Notice notice = noticeDao.selectById(noticeId);
    if (notice == null) {
      throw new IllegalArgumentException("お知らせが見つかりません");
    }
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(admin);
    if (accessible != null && !accessible.contains(notice.getOrganizationId())) {
      throw new IllegalArgumentException("このお知らせを削除する権限がありません");
    }
    notice.setDeletedAt(java.time.LocalDateTime.now());
    noticeDao.update(notice);
  }
}
