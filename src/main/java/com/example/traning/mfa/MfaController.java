package com.example.traning.mfa;

import com.example.traning.audit.AuditLog;
import com.example.traning.user.User;
import com.example.traning.user.service.LoginAttemptService;
import com.example.traning.user.service.UserService;
import dev.samstevens.totp.exceptions.QrGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Slf4j
public class MfaController {

  private final MfaService mfaService;
  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;

  public MfaController(
      MfaService mfaService,
      UserService userService,
      PasswordEncoder passwordEncoder,
      LoginAttemptService loginAttemptService) {
    this.mfaService = mfaService;
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
    this.loginAttemptService = loginAttemptService;
  }

  // ─── 2FA設定画面 ───────────────────────────────────────────────────

  @GetMapping("/user/mfa")
  public String mfaSettings(Model model, Principal principal) {
    User loginUser = userService.getUserByEmail(principal.getName());
    Long userId = loginUser.getUserId().longValue();
    boolean enabled = mfaService.isEnabled(userId);
    model.addAttribute("loginUser", loginUser);
    model.addAttribute("mfaEnabled", enabled);
    return "user/mfa";
  }

  @GetMapping("/user/mfa/setup")
  public String mfaSetup(Model model, Principal principal) {
    User loginUser = userService.getUserByEmail(principal.getName());
    Long userId = loginUser.getUserId().longValue();

    if (mfaService.isEnabled(userId)) {
      return "redirect:/user/mfa";
    }

    try {
      MfaSetupDto setup = mfaService.generateSetup(userId, loginUser.getEmail());
      model.addAttribute("loginUser", loginUser);
      model.addAttribute("secret", setup.getSecret());
      model.addAttribute("qrDataUri", setup.getQrDataUri());
    } catch (QrGenerationException e) {
      log.error("QR code generation failed for userId={}", userId, e);
      model.addAttribute("error", "QRコードの生成に失敗しました。");
      return "user/mfa";
    }
    return "user/mfa/setup";
  }

  @AuditLog(action = "MFA_ENABLE", targetTable = "user_mfa_settings")
  @PostMapping("/user/mfa/enable")
  public String mfaEnable(
      @RequestParam("otp") String otp,
      Principal principal,
      Model model,
      RedirectAttributes redirectAttributes) {
    User loginUser = userService.getUserByEmail(principal.getName());
    Long userId = loginUser.getUserId().longValue();

    try {
      List<String> backupCodes = mfaService.enableMfa(userId, otp);
      redirectAttributes.addFlashAttribute("backupCodes", backupCodes);
      redirectAttributes.addFlashAttribute("success", "2段階認証を有効化しました。バックアップコードを安全な場所に保存してください。");
      return "redirect:/user/mfa/backup-codes";
    } catch (IllegalArgumentException e) {
      try {
        MfaSetupDto setup = mfaService.generateSetup(userId, loginUser.getEmail());
        model.addAttribute("loginUser", loginUser);
        model.addAttribute("secret", setup.getSecret());
        model.addAttribute("qrDataUri", setup.getQrDataUri());
        model.addAttribute("error", e.getMessage());
      } catch (QrGenerationException qrEx) {
        log.error("QR code re-generation failed", qrEx);
      }
      return "user/mfa/setup";
    } catch (IllegalStateException e) {
      redirectAttributes.addFlashAttribute("error", e.getMessage());
      return "redirect:/user/mfa/setup";
    }
  }

  @AuditLog(action = "MFA_DISABLE", targetTable = "user_mfa_settings")
  @PostMapping("/user/mfa/disable")
  public String mfaDisable(
      @RequestParam("password") String password,
      Principal principal,
      RedirectAttributes redirectAttributes) {
    User loginUser = userService.getUserByEmail(principal.getName());

    if (loginUser.getPassword() == null || loginUser.getPassword().isEmpty()) {
      redirectAttributes.addFlashAttribute("error", "OAuth2アカウントは2FA設定の変更ができません。");
      return "redirect:/user/mfa";
    }

    if (!passwordEncoder.matches(password, loginUser.getPassword())) {
      redirectAttributes.addFlashAttribute("error", "パスワードが正しくありません。");
      return "redirect:/user/mfa";
    }

    mfaService.disableMfa(loginUser.getUserId().longValue());
    redirectAttributes.addFlashAttribute("success", "2段階認証を無効化しました。");
    return "redirect:/user/mfa";
  }

  @GetMapping("/user/mfa/backup-codes")
  public String mfaBackupCodes(Model model, Principal principal) {
    User loginUser = userService.getUserByEmail(principal.getName());
    model.addAttribute("loginUser", loginUser);
    return "user/mfa/backup-codes";
  }

  @AuditLog(action = "MFA_BACKUP_REGEN", targetTable = "mfa_backup_codes")
  @PostMapping("/user/mfa/backup-codes/regenerate")
  public String mfaRegenerateBackupCodes(
      Principal principal, RedirectAttributes redirectAttributes) {
    User loginUser = userService.getUserByEmail(principal.getName());
    Long userId = loginUser.getUserId().longValue();

    if (!mfaService.isEnabled(userId)) {
      redirectAttributes.addFlashAttribute("error", "2段階認証が有効になっていません。");
      return "redirect:/user/mfa";
    }

    List<String> codes = mfaService.regenerateBackupCodes(userId);
    redirectAttributes.addFlashAttribute("backupCodes", codes);
    redirectAttributes.addFlashAttribute("success", "バックアップコードを再生成しました。");
    return "redirect:/user/mfa/backup-codes";
  }

  // ─── ログイン時2FA検証 ─────────────────────────────────────────────

  @GetMapping("/auth/mfa")
  public String mfaVerifyPage(HttpServletRequest req, Model model) {
    HttpSession session = req.getSession(false);
    if (session == null || session.getAttribute("MFA_PENDING_USER_ID") == null) {
      return "redirect:/login";
    }
    return "auth/mfa";
  }

  @PostMapping("/auth/mfa/verify")
  public String mfaVerify(
      @RequestParam(value = "otp", required = false) String otp,
      @RequestParam(value = "backupCode", required = false) String backupCode,
      HttpServletRequest req,
      Model model,
      RedirectAttributes redirectAttributes) {

    HttpSession session = req.getSession(false);
    if (session == null) {
      return "redirect:/login";
    }

    Long userId = (Long) session.getAttribute("MFA_PENDING_USER_ID");
    if (userId == null) {
      return "redirect:/login";
    }

    String lockKey = "mfa:" + userId;
    if (loginAttemptService.isBlocked(lockKey)) {
      model.addAttribute("error", "試行回数が上限に達しました。15分後に再試行してください。");
      return "auth/mfa";
    }

    boolean verified = false;

    if (otp != null && !otp.isBlank()) {
      UserMfaSetting setting = mfaService.getSetting(userId).orElse(null);
      if (setting != null) {
        verified = mfaService.verifyOtp(setting.getSecretKey(), otp.trim());
      }
    } else if (backupCode != null && !backupCode.isBlank()) {
      verified = mfaService.verifyBackupCode(userId, backupCode);
    }

    if (verified) {
      loginAttemptService.loginSucceeded(lockKey);
      session.removeAttribute("MFA_PENDING_USER_ID");
      return "redirect:/menu";
    } else {
      loginAttemptService.loginFailed(lockKey);
      model.addAttribute("error", "コードが正しくありません。もう一度お試しください。");
      return "auth/mfa";
    }
  }
}
