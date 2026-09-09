package com.example.traning.mobile.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.dao.UserDao;
import com.example.traning.mobile.dto.MobileProfileResponse;
import com.example.traning.mobile.dto.UpdateProfileRequest;
import com.example.traning.user.User;
import com.example.traning.user.service.ProfileService;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ita7-1: モバイルのプロフィール編集エンドポイントを検証する。 */
@ExtendWith(MockitoExtension.class)
class MobileProfileControllerTest {

  @Mock private UserDao userDao;
  @Mock private ProfileService profileService;

  private MobileProfileController controller;

  @BeforeEach
  void setUp() {
    controller = new MobileProfileController(userDao, profileService);
  }

  private User user() {
    return User.builder()
        .userId(5)
        .email("test@example.com")
        .userName("テスト太郎")
        .heightCm(170.0)
        .weightKg(65.0)
        .gender("MALE")
        .birthDate(LocalDate.of(1990, 1, 1))
        .currentGoalMode("BULKING")
        .aiAdviceConsent(true)
        .build();
  }

  @Test
  void get_モバイル専用DTOに変換して返す() {
    when(userDao.selectById(5)).thenReturn(user());

    ResponseEntity<MobileProfileResponse> response = controller.get(5L);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MobileProfileResponse dto = response.getBody();
    assertThat(dto.userName()).isEqualTo("テスト太郎");
    assertThat(dto.currentGoalMode()).isEqualTo("BULKING");
    assertThat(dto.aiAdviceConsent()).isTrue();
  }

  @Test
  void update_userIdからemailを解決してProfileServiceに委譲する() {
    when(userDao.selectById(5)).thenReturn(user());
    UpdateProfileRequest req = new UpdateProfileRequest();
    req.setUserName("新しい名前");
    req.setHeightCm(175.0);
    req.setWeightKg(68.0);
    req.setGender("MALE");
    req.setBirthDate(LocalDate.of(1990, 1, 1));

    ResponseEntity<Void> response = controller.update(5L, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(profileService)
        .updateProfile(
            org.mockito.ArgumentMatchers.eq("test@example.com"),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void updateGoalMode_204を返しIntegerのuserIdでサービスに委譲する() {
    ResponseEntity<Void> response = controller.updateGoalMode(5L, Map.of("goalMode", "CUTTING"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(profileService).updateGoalMode(5, "CUTTING");
  }

  @Test
  void updateAiAdviceConsent_204を返しサービスに委譲する() {
    ResponseEntity<Void> response =
        controller.updateAiAdviceConsent(5L, Map.of("aiAdviceConsent", true));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(profileService).updateAiAdviceConsent(5, true);
  }
}
