package com.example.traning.mobile.controller;

import com.example.traning.audit.AuditLog;
import com.example.traning.smarttrainer.recommendation.DailyRecommendation;
import com.example.traning.smarttrainer.recommendation.GoalMode;
import com.example.traning.smarttrainer.recommendation.RecommendationService;
import com.example.traning.user.service.ProfileService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F3 Phase1: モバイル向けルールベース推奨エンジンAPI。 */
@RestController
@RequestMapping("/api/mobile")
public class MobileRecommendationController {

  private final RecommendationService recommendationService;
  private final ProfileService profileService;

  public MobileRecommendationController(
      RecommendationService recommendationService, ProfileService profileService) {
    this.recommendationService = recommendationService;
    this.profileService = profileService;
  }

  /** 今日のおすすめメニューを取得する。 */
  @GetMapping("/recommendations/today")
  public ResponseEntity<DailyRecommendation> getTodayRecommendation(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(recommendationService.getTodayRecommendation(userId));
  }

  /** 目的モード（筋肥大/減量/維持）を変更する。 */
  @PostMapping("/profile/goal-mode")
  @Transactional
  @AuditLog(action = "MOBILE_PROFILE_GOAL_MODE_UPDATE", targetTable = "users")
  public ResponseEntity<Void> updateGoalMode(
      @AuthenticationPrincipal Long userId, @RequestBody Map<String, String> body) {
    String goalMode = GoalMode.fromString(body.get("goalMode")).name();
    profileService.updateGoalMode(userId.intValue(), goalMode);
    return ResponseEntity.noContent().build();
  }
}
