package com.example.traning.mobile.dto;

import com.example.traning.user.User;
import java.time.LocalDate;

/** モバイル: プロフィール取得API（GET /api/mobile/profile）のレスポンス（ita7-1）。 */
public record MobileProfileResponse(
    String userName,
    Double heightCm,
    Double weightKg,
    String gender,
    LocalDate birthDate,
    String currentGoalMode,
    Boolean aiAdviceConsent) {

  public static MobileProfileResponse from(User user) {
    return new MobileProfileResponse(
        user.getUserName(),
        user.getHeightCm(),
        user.getWeightKg(),
        user.getGender(),
        user.getBirthDate(),
        user.getCurrentGoalMode(),
        user.getAiAdviceConsent());
  }
}
