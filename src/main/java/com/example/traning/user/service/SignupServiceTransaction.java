package com.example.traning.user.service;

import com.example.traning.dao.UserDao;
import com.example.traning.organization.InviteCode;
import com.example.traning.organization.InviteCodeService;
import com.example.traning.organization.Organization;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import com.example.traning.user.form.SignupForm;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class SignupServiceTransaction {

  private final UserDao userDao;
  private final PasswordEncoder passwordEncoder;
  private final AccountRestoreService accountRestoreService;
  private final InviteCodeService inviteCodeService;

  public SignupServiceTransaction(
      UserDao userDao,
      PasswordEncoder passwordEncoder,
      AccountRestoreService accountRestoreService,
      InviteCodeService inviteCodeService) {
    this.userDao = userDao;
    this.passwordEncoder = passwordEncoder;
    this.accountRestoreService = accountRestoreService;
    this.inviteCodeService = inviteCodeService;
  }

  private static final String EMAIL_DUPLICATE_MESSAGE =
      "この内容では登録できませんでした。入力内容をご確認いただくか、既にアカウントをお持ちの場合はログインをお試しください。";

  @Transactional(rollbackFor = Exception.class)
  public boolean execute(SignupForm signupForm) {
    // 論理削除済みユーザーが同じメールで再登録しようとした場合は復元フローへ
    if (userDao.selectSoftDeletedByEmail(signupForm.getEmail()).isPresent()) {
      accountRestoreService.initiateRestore(signupForm.getEmail());
      throw new AccountRestoreRequiredException();
    }
    // 有効な既存ユーザーとのメール重複（事前チェック。TOCTOU対策は insert 側で別途行う）
    if (userDao.selectByEmail(signupForm.getEmail()).isPresent()) {
      throw new EmailDuplicateException(EMAIL_DUPLICATE_MESSAGE);
    }

    User user = new User();
    user.setEmail(signupForm.getEmail());

    // OAuth2経由の場合は仮パスワードを生成してハッシュ化して保存する
    if (signupForm.isOAuth2Signup()) {
      String temporaryPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
      user.setPassword(passwordEncoder.encode(temporaryPassword));
      log.info("OAuth2 user registered - userId will be assigned by DB");
    } else {
      // フォーム登録の場合は既にエンコード済みのパスワード
      user.setPassword(signupForm.getPassword());
    }

    user.setUserName(signupForm.getUsername());
    user.setRole(Role.USER.value());
    user.setEnabled(true);
    user.setGoogleId(signupForm.getGoogleId() != null ? signupForm.getGoogleId() : null);
    user.setLineId(signupForm.getLineId() != null ? signupForm.getLineId() : null);
    user.setCreateDatetime(LocalDateTime.now());
    user.setUpdatedDatetime(LocalDateTime.now());
    user.setHeightCm(signupForm.getHeightCm());
    user.setWeightKg(signupForm.getWeightKg());
    user.setGender(
        signupForm.getGender() != null && !signupForm.getGender().isBlank()
            ? signupForm.getGender()
            : null);
    user.setBirthDate(signupForm.getBirthDate());
    // current_goal_modeはDB上NOT NULL。Domaの自動生成INSERTは全列を明示的に列挙するため、
    // ここで未設定のままだとNULLが明示的にバインドされDBのDEFAULT句が効かず登録に失敗する。
    user.setCurrentGoalMode(GoalMode.MAINTENANCE.name());
    // organization_idも同様にDB上NOT NULL。招待コード入力時はその組織へ、未入力時は
    // 一般ユーザー向けデフォルト組織へ割り当てる（ita3-3）。無効なコードはIllegalArgumentExceptionで拒否。
    Long organizationId = Organization.DEFAULT_STORE_ORGANIZATION_ID;
    if (signupForm.getInviteCode() != null && !signupForm.getInviteCode().isBlank()) {
      InviteCode inviteCode = inviteCodeService.redeem(signupForm.getInviteCode().trim());
      organizationId = inviteCode.getOrganizationId();
    }
    user.setOrganizationId(organizationId);
    // notification_method/line_friend_addedもDB上NOT NULL。LINEサインアップ者は通知方法をLINEに寄せる
    // （まだ公式アカウントの友だち追加はしていないためline_friend_addedはfalseで初期化、追加後はWebhookで更新される）。
    user.setNotificationMethod(signupForm.getLineId() != null ? "LINE" : "EMAIL");
    user.setLineFriendAdded(false);

    try {
      userDao.insert(user);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      // 事前チェックをすり抜けた同時登録（TOCTOU）を想定内として扱う。
      // doma-spring-bootの永続化例外変換により、org.seasar.doma.jdbc.UniqueConstraintExceptionは
      // ここに到達する前にDuplicateKeyExceptionへ変換される（実機検証で判明）。
      throw new EmailDuplicateException(EMAIL_DUPLICATE_MESSAGE);
    } catch (Exception e) {
      // ここに到達するのは本当に想定外のDB例外のみ
      log.error("Failed to register user - email: {}", signupForm.getEmail(), e);
      throw e;
    }
    log.info("User registered successfully - isOAuth2: {}", signupForm.isOAuth2Signup());
    return true;
  }
}
