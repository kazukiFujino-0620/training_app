package com.example.traning.training.controller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.training.service.TrainingService;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

/** 機能見直し-1-#15: 管理者用の記録取得API（/admin/api/**）をトレーナーが使う場合の範囲を検証する。 */
@ExtendWith(MockitoExtension.class)
class TrainingApiControllerTrainerAccessTest {

  @Mock private TrainingService trainingService;
  @Mock private UserService userService;
  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;
  @Mock private UserDetails userDetails;

  private TrainingApiController controller;

  private final User trainer = user(1, "ROLE_TRAINER", 10L, null);
  private final User sameStoreOther = user(101, "ROLE_USER", 10L, 99L);
  private final User otherStore = user(102, "ROLE_USER", 20L, null);

  private static User user(int id, String role, Long organizationId, Long assignedTrainerId) {
    return User.builder()
        .userId(id)
        .email("u" + id + "@example.com")
        .role(role)
        .organizationId(organizationId)
        .assignedTrainerId(assignedTrainerId)
        .build();
  }

  @BeforeEach
  void setUp() {
    OrganizationScopeResolver resolver =
        new OrganizationScopeResolver(organizationDao, userStoreAccessDao);
    controller =
        new TrainingApiController(
            trainingService, userService, resolver, new MemberAccessPolicy(resolver));
    when(userDetails.getUsername()).thenReturn(trainer.getEmail());
    when(userService.getUserByEmail(trainer.getEmail())).thenReturn(trainer);
    lenient().when(userService.getUserById(101)).thenReturn(sameStoreOther);
    lenient().when(userService.getUserById(102)).thenReturn(otherStore);
    lenient()
        .when(userStoreAccessDao.selectStoreOrganizationIdsByUserId(anyLong()))
        .thenReturn(List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void 会員を指定しない全員分の取得はトレーナーには403() {
    assertThatThrownBy(() -> controller.getDetails("2026-11-14", null))
        .isInstanceOf(ResponseStatusException.class);
    verify(trainingService, never()).findByDate(any(), any());
  }

  @Test
  void 同じ店舗の会員の記録は取得できる() {
    when(trainingService.findByUserIdAndDate(101L, "2026-11-14")).thenReturn(List.of());
    assertThat(controller.getDetails("2026-11-14", 101L)).isEmpty();
  }

  @Test
  void 他の店舗の会員の記録とボリュームは403() {
    assertThatThrownBy(() -> controller.getDetails("2026-11-14", 102L))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> controller.getTrainingVolume(102L, "2026-11-01", "2026-11-14"))
        .isInstanceOf(ResponseStatusException.class);
    verify(trainingService, never()).makeChartDataCustom(any(), any(), any());
  }
}
