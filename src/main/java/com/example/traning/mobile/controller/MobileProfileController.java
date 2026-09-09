package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.dao.UserDao;
import com.example.traning.mobile.dto.MobileProfileResponse;
import com.example.traning.mobile.dto.UpdateProfileRequest;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.user.User;
import com.example.traning.user.form.ProfileForm;
import com.example.traning.user.service.ProfileService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * モバイル: プロフィール編集API（ita7-1）。
 *
 * <p>既存の{@link ProfileService}（{@code updateProfile(String email, ProfileForm form)} / {@code
 * updateGoalMode(Integer, String)} / {@code updateAiAdviceConsent(Integer, boolean)}）をそのまま流用する。
 * {@code updateProfile}は引数がemailだが、モバイル側はuserId（Long）しか持たないため、{@link UserDao#selectById(Integer)}で
 * Userを取得しそのemailを渡す方式にする（既存メソッドのシグネチャは変更しない）。
 */
@RestController
@RequestMapping("/api/mobile/profile")
@PreAuthorize("isAuthenticated()")
public class MobileProfileController {

  private final UserDao userDao;
  private final ProfileService profileService;

  public MobileProfileController(UserDao userDao, ProfileService profileService) {
    this.userDao = userDao;
    this.profileService = profileService;
  }

  @GetMapping
  public ResponseEntity<MobileProfileResponse> get(@AuthenticationPrincipal Long userId) {
    User user = userDao.selectById(userId.intValue());
    return ResponseEntity.ok(MobileProfileResponse.from(user));
  }

  @PatchMapping
  @Transactional
  @AuditLog(action = "MOBILE_PROFILE_UPDATE", targetTable = "users")
  public ResponseEntity<Void> update(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest req) {
    User user = userDao.selectById(userId.intValue());
    ProfileForm form = req.toProfileForm();
    profileService.updateProfile(user.getEmail(), form);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/goal-mode")
  @Transactional
  @AuditLog(action = "MOBILE_PROFILE_GOAL_MODE_UPDATE", targetTable = "users")
  public ResponseEntity<Void> updateGoalMode(
      @AuthenticationPrincipal Long userId, @RequestBody Map<String, String> body) {
    profileService.updateGoalMode(
        userId.intValue(), GoalMode.fromString(body.get("goalMode")).name());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/ai-advice-consent")
  @Transactional
  @AuditLog(action = "MOBILE_PROFILE_AI_ADVICE_CONSENT_UPDATE", targetTable = "users")
  public ResponseEntity<Void> updateAiAdviceConsent(
      @AuthenticationPrincipal Long userId, @RequestBody Map<String, Boolean> body) {
    profileService.updateAiAdviceConsent(
        userId.intValue(), Boolean.TRUE.equals(body.get("aiAdviceConsent")));
    return ResponseEntity.noContent().build();
  }
}
