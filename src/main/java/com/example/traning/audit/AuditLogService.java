package com.example.traning.audit;

import com.example.traning.dao.UserDao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

  private static final int PAGE_SIZE = 50;

  private final AuditLogDao auditLogDao;
  private final UserDao userDao;

  @Transactional
  public void record(
      Long userId,
      String action,
      String targetTable,
      Long targetId,
      String ipAddress,
      String requestPath) {
    AuditLogEntry entry = new AuditLogEntry();
    entry.setUserId(userId);
    entry.setAction(action);
    entry.setTargetTable(targetTable);
    entry.setTargetId(targetId);
    entry.setIpAddress(ipAddress != null ? ipAddress : "unknown");
    entry.setRequestPath(requestPath != null ? requestPath : "");
    entry.setChangedAt(LocalDateTime.now());
    // organization_idはNULL許容だが、組織管理者向けのログ絞り込み（フェーズ3）に備え、
    // 判明する場合はここで補完しておく（userIdが無い/不明な場合はNULLのまま）。
    if (userId != null) {
      entry.setOrganizationId(userDao.selectOrganizationIdById(userId));
    }
    auditLogDao.insert(entry);
    log.debug("監査ログ記録: action={} userId={} targetId={}", action, userId, targetId);
  }

  public List<AuditLogEntry> findForAdmin(
      Long userId, String action, LocalDate from, LocalDate to, int page) {
    int offset = (page - 1) * PAGE_SIZE;
    return auditLogDao.selectForAdmin(userId, action, from, to, offset, PAGE_SIZE);
  }

  public int countForAdmin(Long userId, String action, LocalDate from, LocalDate to) {
    return auditLogDao.countForAdmin(userId, action, from, to);
  }
}
