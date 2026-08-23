package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.organization.InviteCode;
import com.example.traning.organization.InviteCodeService;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.form.TrainerSignupForm;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * トレーナー用新規登録（ita4-3）。一般ユーザー向け{@link SignupServiceTransaction}とは別ルートで、招待コードにより 所属組織（自店舗）を決定し、{@link
 * Role#TRAINER}として登録する。
 */
@Service
@Slf4j
public class TrainerSignupService {

  private final UserDao userDao;
  private final InviteCodeService inviteCodeService;
  private final PasswordEncoder passwordEncoder;
  private final AccountRestoreService accountRestoreService;

  public TrainerSignupService(
      UserDao userDao,
      InviteCodeService inviteCodeService,
      PasswordEncoder passwordEncoder,
      AccountRestoreService accountRestoreService) {
    this.userDao = userDao;
    this.inviteCodeService = inviteCodeService;
    this.passwordEncoder = passwordEncoder;
    this.accountRestoreService = accountRestoreService;
  }

  /**
   * @throws IllegalArgumentException パスワード不一致、または招待コードが無効な場合（{@link
   *     InviteCodeService#redeem}の例外をそのまま透過）
   * @throws AccountRestoreRequiredException 論理削除済みユーザーが同じメールで再登録しようとした場合
   */
  @Transactional(rollbackFor = Exception.class)
  public void register(TrainerSignupForm form) {
    if (!form.getPassword().equals(form.getPassword_confirm())) {
      throw new IllegalArgumentException("パスワードが一致しません");
    }

    if (userDao.selectSoftDeletedByEmail(form.getEmail()).isPresent()) {
      accountRestoreService.initiateRestore(form.getEmail());
      throw new AccountRestoreRequiredException();
    }

    // 招待コードの検証・使用回数インクリメントを先に行う。ここで例外が出れば
    // ユーザー登録自体もロールバックされる（@Transactionalによりこのメソッド全体が1トランザクション）。
    InviteCode inviteCode = inviteCodeService.redeem(form.getInviteCode());

    User user = new User();
    user.setEmail(form.getEmail());
    user.setPassword(passwordEncoder.encode(form.getPassword()));
    user.setUserName(form.getUsername());
    user.setRole(Role.TRAINER.value());
    user.setEnabled(true);
    user.setCreateDatetime(LocalDateTime.now());
    user.setUpdatedDatetime(LocalDateTime.now());
    // current_goal_mode/organization_id/notification_method/line_friend_addedはDB上NOT NULL。
    // Domaの自動生成INSERTは全列を明示的に列挙するため、ここで未設定のままだとNULLが明示的に
    // バインドされDBのDEFAULT句が効かず登録に失敗する（ita1-1/ita2結合試験で発見した既知パターン）。
    user.setCurrentGoalMode(GoalMode.MAINTENANCE.name());
    user.setOrganizationId(inviteCode.getOrganizationId());
    user.setNotificationMethod("EMAIL");
    user.setLineFriendAdded(false);

    userDao.insert(user);
    log.info(
        "Trainer registered successfully - organizationId: {}", inviteCode.getOrganizationId());
  }
}
