package com.example.traning.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.traning.body.BodyMeasurementService;
import com.example.traning.export.DataExportService;
import com.example.traning.mfa.MfaService;
import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.organization.UserStoreAccessDao;
import com.example.traning.training.dao.TrainingDao;
import com.example.traning.training.dao.TrainingDetailDao;
import com.example.traning.training.service.CalorieCalculator;
import com.example.traning.user.Role;
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
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * ita3-3: ROLE_ADMIN（全組織アクセス可）であっても、一般ユーザー（デフォルト組織所属）は `/admin/users`・`/admin/all-users-training`
 * の一覧から除外されることを検証する。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerGeneralUserFilterTest {

  @Mock private UserService userService;
  @Mock private TrainingDao trainingDao;
  @Mock private TrainingDetailDao trainingDetailDao;
  @Mock private CalorieCalculator calorieCalculator;
  @Mock private MfaService mfaService;
  @Mock private DataExportService dataExportService;
  @Mock private BodyMeasurementService bodyMeasurementService;
  @Mock private OrganizationScopeResolver organizationScopeResolver;
  @Mock private OrganizationDao organizationDao;
  @Mock private UserStoreAccessDao userStoreAccessDao;
  @Mock private UserDetails userDetails;

  private AdminController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminController(
            userService,
            trainingDao,
            trainingDetailDao,
            calorieCalculator,
            mfaService,
            dataExportService,
            bodyMeasurementService,
            organizationScopeResolver,
            organizationDao,
            userStoreAccessDao);

    User admin = new User();
    admin.setUserId(1);
    admin.setEmail("admin@example.com");
    admin.setRole(Role.ADMIN.value());
    when(userDetails.getUsername()).thenReturn("admin@example.com");
    when(userService.getUserByEmail("admin@example.com")).thenReturn(admin);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null, List.of()));
    when(organizationScopeResolver.resolveAccessibleOrganizationIds(admin)).thenReturn(null);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listUsers_ROLE_ADMINでも一般ユーザーは一覧から除外される() {
    User generalUser = new User();
    generalUser.setUserId(2);
    generalUser.setOrganizationId(Organization.DEFAULT_STORE_ORGANIZATION_ID);
    User gymUser = new User();
    gymUser.setUserId(3);
    gymUser.setOrganizationId(7L);
    when(userService.findAll()).thenReturn(List.of(generalUser, gymUser));

    Model model = new ExtendedModelMap();
    controller.listUsers(model);

    @SuppressWarnings("unchecked")
    List<User> userList = (List<User>) model.getAttribute("userList");
    assertThat(userList).containsExactly(gymUser);
  }

  @Test
  void showAllUsersTrainingList_ROLE_ADMINでも一般ユーザーは一覧から除外される() {
    User generalUser = new User();
    generalUser.setUserId(2);
    generalUser.setOrganizationId(Organization.DEFAULT_STORE_ORGANIZATION_ID);
    User gymUser = new User();
    gymUser.setUserId(3);
    gymUser.setOrganizationId(7L);
    when(userService.findAll()).thenReturn(List.of(generalUser, gymUser));

    Model model = new ExtendedModelMap();
    controller.showAllUsersTrainingList(null, model);

    @SuppressWarnings("unchecked")
    List<User> userList = (List<User>) model.getAttribute("userList");
    assertThat(userList).containsExactly(gymUser);
  }
}
