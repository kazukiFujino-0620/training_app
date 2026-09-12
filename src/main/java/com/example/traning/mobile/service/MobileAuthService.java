package com.example.traning.mobile.service;

import com.example.traning.dao.UserDao;
import com.example.traning.mfa.MfaService;
import com.example.traning.mfa.UserMfaSetting;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.mobile.dto.LoginRequest;
import com.example.traning.mobile.dto.MfaVerifyRequest;
import com.example.traning.mobile.dto.RefreshRequest;
import com.example.traning.mobile.dto.TokenResponse;
import com.example.traning.mobile.entity.MobileRefreshToken;
import com.example.traning.user.User;
import com.example.traning.user.service.LoginAttemptService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class MobileAuthService {

  private static final long ACCESS_TOKEN_EXPIRES_IN_SEC = 15 * 60L;

  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;
  private final UserDao userDao;
  private final MobileRefreshTokenDao refreshTokenDao;
  private final MfaService mfaService;
  private final LoginAttemptService loginAttemptService;

  public MobileAuthService(
      JwtService jwtService,
      PasswordEncoder passwordEncoder,
      UserDao userDao,
      MobileRefreshTokenDao refreshTokenDao,
      MfaService mfaService,
      LoginAttemptService loginAttemptService) {
    this.jwtService = jwtService;
    this.passwordEncoder = passwordEncoder;
    this.userDao = userDao;
    this.refreshTokenDao = refreshTokenDao;
    this.mfaService = mfaService;
    this.loginAttemptService = loginAttemptService;
  }

  @Transactional
  public TokenResponse login(LoginRequest req) {
    // ブルートフォース対策チェック
    if (loginAttemptService.isBlocked(req.getEmail())) {
      throw new IllegalArgumentException("アカウントがロックされています。しばらくしてから再試行してください。");
    }

    User user = userDao.selectByEmail(req.getEmail()).orElse(null);

    // OAuthユーザー（Google/LINE）はパスワードログイン不可
    if (user != null && (user.getGoogleId() != null || user.getLineId() != null)) {
      throw new com.example.traning.mobile.exception.OAuthOnlyException();
    }

    if (user == null
        || user.getPassword() == null
        || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
      loginAttemptService.loginFailed(req.getEmail());
      throw new IllegalArgumentException("メールアドレスまたはパスワードが正しくありません");
    }
    loginAttemptService.loginSucceeded(req.getEmail());

    if (Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
      throw new IllegalArgumentException("アカウントが無効です");
    }

    Long userId = user.getUserId().longValue();

    // MFAが有効な場合は仮トークンを返す
    if (mfaService.isEnabled(userId)) {
      String mfaTempToken = jwtService.generateMfaTempToken(userId, req.getDeviceId());
      return TokenResponse.mfaPending(mfaTempToken);
    }

    return issueFullTokens(
        userId, user.getEmail(), user.getRole(), req.getDeviceId(), user.getUserName());
  }

  /**
   * OAuth（Google/LINE）でのログイン。パスワード検証は行わず、呼び出し元（{@code MobileOAuthLoginService}）が 解決済みの{@link
   * User}を渡す。MFAが有効な場合は仮トークンを返し、モバイル側は既存の{@code /mfa/verify} フローへ合流する。
   *
   * @throws IllegalArgumentException アカウントが無効な場合
   */
  @Transactional
  public TokenResponse loginViaOAuth(User user, String deviceId) {
    if (Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
      throw new IllegalArgumentException("アカウントが無効です");
    }

    Long userId = user.getUserId().longValue();

    if (mfaService.isEnabled(userId)) {
      String mfaTempToken = jwtService.generateMfaTempToken(userId, deviceId);
      return TokenResponse.mfaPending(mfaTempToken);
    }

    return issueFullTokens(userId, user.getEmail(), user.getRole(), deviceId, user.getUserName());
  }

  @Transactional
  public TokenResponse verifyMfa(MfaVerifyRequest req) {
    Claims claims;
    try {
      claims = jwtService.parseMfaTempToken(req.getMfaTempToken());
    } catch (JwtException e) {
      throw new IllegalArgumentException("MFA仮トークンが無効または期限切れです");
    }

    Long userId = Long.parseLong(claims.getSubject());
    String deviceId = claims.get("deviceId", String.class);

    // MFA仮トークン発行時のdeviceIdと、検証リクエストのdeviceIdが一致することを確認する。
    // 不一致の場合、トークンが別デバイスに漏洩・転用された可能性があるため拒否する。
    if (deviceId == null || !deviceId.equals(req.getDeviceId())) {
      log.warn("MFA検証: deviceId不一致のため拒否 - userId: {}", userId);
      throw new IllegalArgumentException("MFA仮トークンが無効または期限切れです");
    }

    UserMfaSetting setting =
        mfaService
            .getSetting(userId)
            .orElseThrow(() -> new IllegalArgumentException("MFA設定が見つかりません"));

    boolean valid = false;
    if (req.getOtp() != null && !req.getOtp().isBlank()) {
      valid = mfaService.verifyOtp(setting.getSecretKey(), req.getOtp());
    } else if (req.getBackupCode() != null && !req.getBackupCode().isBlank()) {
      valid = mfaService.verifyBackupCode(userId, req.getBackupCode());
    }

    if (!valid) {
      throw new IllegalArgumentException("認証コードが正しくありません");
    }

    User user = userDao.selectById(userId.intValue());
    if (user == null || Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
      throw new IllegalArgumentException("ユーザーが見つかりません");
    }

    return issueFullTokens(userId, user.getEmail(), user.getRole(), deviceId, user.getUserName());
  }

  @Transactional
  public TokenResponse refresh(RefreshRequest req) {
    MobileRefreshToken stored =
        refreshTokenDao.selectActiveByDeviceId(req.getDeviceId()).stream()
            .filter(t -> passwordEncoder.matches(req.getRefreshToken(), t.getTokenHash()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("リフレッシュトークンが無効です"));

    if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
      throw new IllegalArgumentException("リフレッシュトークンが期限切れです");
    }

    User user = userDao.selectById(stored.getUserId().intValue());
    if (user == null || Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
      throw new IllegalArgumentException("ユーザーが見つかりません");
    }

    // 旧トークンを無効化してから新規発行
    refreshTokenDao.revokeByTokenHash(stored.getTokenHash(), LocalDateTime.now());

    return issueFullTokens(
        stored.getUserId(),
        user.getEmail(),
        user.getRole(),
        stored.getDeviceId(),
        user.getUserName());
  }

  @Transactional
  public void logout(Long userId, String deviceId) {
    refreshTokenDao.deleteByUserIdAndDeviceId(userId, deviceId);
  }

  /** アクセストークン + リフレッシュトークンを発行して TokenResponse を返す */
  private TokenResponse issueFullTokens(
      Long userId, String email, String role, String deviceId, String userName) {
    String accessToken = jwtService.generateAccessToken(userId, email, role);

    String rawRefreshToken = UUID.randomUUID().toString();
    String tokenHash = passwordEncoder.encode(rawRefreshToken);

    refreshTokenDao.deleteByUserIdAndDeviceId(userId, deviceId);

    MobileRefreshToken entity = new MobileRefreshToken();
    entity.setUserId(userId);
    entity.setTokenHash(tokenHash);
    entity.setDeviceId(deviceId);
    entity.setExpiresAt(
        LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenValidityMs() / 1000));
    refreshTokenDao.insert(entity);

    return TokenResponse.full(accessToken, rawRefreshToken, ACCESS_TOKEN_EXPIRES_IN_SEC, userName);
  }
}
