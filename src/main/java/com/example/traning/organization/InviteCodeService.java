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
 * <p>発行できるのはROLE_ADMIN（全組織向け）・ROLE_ORG_ADMIN（自組織向けのみ）。ROLE_STORE_ADMIN・ROLE_TRAINERはこの画面自体にアクセスできない。
 *
 * <p>機能見直し-1-#15・itバグ-44: コードは用途（{@link InviteCodePurpose}）を持ち、会員の新規登録は会員用のみ、トレーナー登録は
 * トレーナー用のみ受け付ける。
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
      Long organizationId,
      InviteCodePurpose purpose,
      LocalDateTime expiresAt,
      Integer maxUses,
      User currentAdmin) {
    Role role = Role.fromValue(currentAdmin.getRole());
    if (role != Role.ADMIN && role != Role.ORG_ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "招待コードの発行権限がありません");
    }
    if (purpose == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "招待コードの用途を選んでください");
    }
    if (!organizationScopeResolver.canAccessOrganization(currentAdmin, organizationId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "自組織向けにのみ発行できます");
    }
    InviteCode inviteCode = new InviteCode();
    inviteCode.setCode(generateCode());
    inviteCode.setPurpose(purpose.name());
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

  /** 会員の新規登録（/signup）に用途違いのコードを入れた場合の文言（機能見直し-1-#15）。 */
  public static final String MEMBER_PURPOSE_MISMATCH_MESSAGE =
      "このコードは会員の新規登録には使えません。ジムから受け取った「会員用」の招待コードを入力してください。";

  /** トレーナー登録（/signup/trainer）に用途違いのコードを入れた場合の文言（仮画面C）。 */
  public static final String TRAINER_PURPOSE_MISMATCH_MESSAGE =
      "このコードはトレーナー登録には使えません。ジムの管理者から「トレーナー用」の招待コードを受け取ってください。";

  /**
   * 招待コードを引き換える。用途（{@code expectedPurpose}）が一致すること、有効性（失効・期限・使用回数上限）を検証した上で
   * used_countをインクリメントし、割り当て先organization_idを含む最新状態を返す。用途が違う場合は使用回数を増やさない。
   *
   * @throws IllegalArgumentException コードが存在しない、用途が違う、または失効・期限切れ・使用回数上限に達している場合
   */
  @Transactional
  public InviteCode redeem(String code, InviteCodePurpose expectedPurpose) {
    InviteCode inviteCode =
        inviteCodeDao
            .selectByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("招待コードが見つかりません"));
    // 用途が読めない値（想定外）は会員用・トレーナー用のどちらにも使わせない
    if (InviteCodePurpose.fromName(inviteCode.getPurpose()) != expectedPurpose) {
      throw new IllegalArgumentException(
          expectedPurpose == InviteCodePurpose.TRAINER
              ? TRAINER_PURPOSE_MISMATCH_MESSAGE
              : MEMBER_PURPOSE_MISMATCH_MESSAGE);
    }
    if (inviteCode.getRevokedAt() != null) {
      throw new IllegalArgumentException("この招待コードは失効しています");
    }
    if (inviteCode.getExpiresAt() != null
        && inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("この招待コードは有効期限が切れています");
    }
    if (inviteCode.getMaxUses() != null && inviteCode.getUsedCount() >= inviteCode.getMaxUses()) {
      throw new IllegalArgumentException("この招待コードは使用回数の上限に達しています");
    }
    inviteCode.setUsedCount(inviteCode.getUsedCount() + 1);
    inviteCodeDao.update(inviteCode);
    return inviteCode;
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
