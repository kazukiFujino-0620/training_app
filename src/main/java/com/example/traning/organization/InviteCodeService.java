package com.example.traning.organization;

import com.example.traning.user.Role;
import com.example.traning.user.User;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 招待コードの発行・一覧・失効を、操作者の権限に応じて制御するサービス（ita1-1 未実施分）。
 *
 * <p>発行できるのはROLE_ADMIN（全組織向け）・ROLE_ORG_ADMIN（自組織向けのみ）。ROLE_STORE_ADMINはこの画面自体にアクセスできない。
 */
@Service
public class InviteCodeService {

  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom random = new SecureRandom();

  private final InviteCodeDao inviteCodeDao;
  private final OrganizationScopeResolver organizationScopeResolver;

  public InviteCodeService(
      InviteCodeDao inviteCodeDao, OrganizationScopeResolver organizationScopeResolver) {
    this.inviteCodeDao = inviteCodeDao;
    this.organizationScopeResolver = organizationScopeResolver;
  }

  public List<InviteCode> listForAdmin(User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role == Role.ADMIN) {
      return inviteCodeDao.selectAll();
    }
    if (role == Role.ORG_ADMIN) {
      Set<Long> accessible =
          organizationScopeResolver.resolveAccessibleOrganizationIds(currentAdmin);
      return inviteCodeDao.selectByOrganizationIds(new ArrayList<>(accessible));
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この画面へのアクセス権限がありません");
  }

  @Transactional
  public InviteCode issue(
      Long organizationId, LocalDateTime expiresAt, Integer maxUses, User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role != Role.ADMIN && role != Role.ORG_ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "招待コードの発行権限がありません");
    }
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, organizationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "自組織向けにのみ発行できます");
    }
    InviteCode inviteCode = new InviteCode();
    inviteCode.setCode(generateCode());
    inviteCode.setOrganizationId(organizationId);
    inviteCode.setExpiresAt(expiresAt);
    inviteCode.setMaxUses(maxUses);
    inviteCode.setUsedCount(0);
    inviteCode.setCreatedBy(currentAdmin.getUserId().longValue());
    inviteCodeDao.insert(inviteCode);
    return inviteCode;
  }

  @Transactional
  public void revoke(Long id, User currentAdmin) {
    InviteCode target =
        inviteCodeDao
            .selectById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "招待コードが見つかりません"));
    if (!organizationScopeResolver.canAccessOrganization(
        currentAdmin, target.getOrganizationId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この招待コードを失効させる権限がありません");
    }
    target.setRevokedAt(LocalDateTime.now());
    inviteCodeDao.update(target);
  }

  /** 紛らわしい文字（0/O、1/I等）を避けた10桁のコードを生成する。 */
  private String generateCode() {
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
    }
    return sb.toString();
  }
}
