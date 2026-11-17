package com.example.traning.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * itバグ-49: 招待コード発行画面のPOSTで、トレーナー用を店舗以外宛てに送られた場合はコードを作らず、画面のエラー表示（errorMessage）に
 * 文言を出して一覧へ戻すことを検証する。サービスは実物を使い、DAOだけをモックする。
 */
@ExtendWith(MockitoExtension.class)
class AdminInviteCodeControllerTest {

  private static final String EMAIL = "admin@example.com";
  private static final Long GYM_ID = 1L;
  private static final Long STORE_ID = 3L;

  @Mock private InviteCodeDao inviteCodeDao;
  @Mock private OrganizationDao organizationDao;
  @Mock private OrganizationScopeResolver organizationScopeResolver;
  @Mock private UserService userService;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    InviteCodeService inviteCodeService =
        new InviteCodeService(inviteCodeDao, organizationScopeResolver, organizationDao);
    AdminInviteCodeController controller =
        new AdminInviteCodeController(
            inviteCodeService, organizationDao, organizationScopeResolver, userService);
    mvc = MockMvcBuilders.standaloneSetup(controller).build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                org.springframework.security.core.userdetails.User.withUsername(EMAIL)
                    .password("x")
                    .roles("ADMIN")
                    .build(),
                null));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private User loginAs(String role) {
    User user = User.builder().userId(1).email(EMAIL).role(role).organizationId(GYM_ID).build();
    when(userService.getUserByEmail(EMAIL)).thenReturn(user);
    return user;
  }

  private void organizationOfType(Long id, String type) {
    Organization organization = new Organization();
    organization.setId(id);
    organization.setType(type);
    when(organizationDao.selectById(id)).thenReturn(Optional.of(organization));
  }

  @Test
  void ADMINがトレーナー用を組織宛てに送るとエラー文言を出して発行しない() throws Exception {
    User admin = loginAs("ROLE_ADMIN");
    when(organizationScopeResolver.canAccessOrganization(admin, GYM_ID)).thenReturn(true);
    organizationOfType(GYM_ID, "GYM");

    mvc.perform(
            post("/admin/invite-codes")
                .param("organizationId", GYM_ID.toString())
                .param("purpose", "TRAINER"))
        .andExpect(redirectedUrl("/admin/invite-codes"))
        .andExpect(flash().attribute("errorMessage", "トレーナー用の招待コードは、店舗を選んで発行してください。"))
        .andExpect(flash().attributeCount(1));

    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void ORG_ADMINがトレーナー用を自組織のジム宛てに送るとエラー文言を出して発行しない() throws Exception {
    User orgAdmin = loginAs("ROLE_ORG_ADMIN");
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, GYM_ID)).thenReturn(true);
    organizationOfType(GYM_ID, "GYM");

    mvc.perform(
            post("/admin/invite-codes")
                .param("organizationId", GYM_ID.toString())
                .param("purpose", "TRAINER")
                .param("maxUses", "1"))
        .andExpect(redirectedUrl("/admin/invite-codes"))
        .andExpect(
            flash().attribute("errorMessage", InviteCodeService.TRAINER_REQUIRES_STORE_MESSAGE));

    verify(inviteCodeDao, never()).insert(any(InviteCode.class));
  }

  @Test
  void ADMINはトレーナー用を店舗宛てに発行できる() throws Exception {
    User admin = loginAs("ROLE_ADMIN");
    when(organizationScopeResolver.canAccessOrganization(admin, STORE_ID)).thenReturn(true);
    organizationOfType(STORE_ID, "STORE");

    mvc.perform(
            post("/admin/invite-codes")
                .param("organizationId", STORE_ID.toString())
                .param("purpose", "TRAINER"))
        .andExpect(redirectedUrl("/admin/invite-codes"))
        .andExpect(flash().attribute("successMessage", "招待コードを発行しました。"))
        .andExpect(flash().attributeCount(1));

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void ORG_ADMINはトレーナー用を自組織の店舗宛てに発行できる() throws Exception {
    User orgAdmin = loginAs("ROLE_ORG_ADMIN");
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, STORE_ID)).thenReturn(true);
    organizationOfType(STORE_ID, "STORE");

    mvc.perform(
            post("/admin/invite-codes")
                .param("organizationId", STORE_ID.toString())
                .param("purpose", "TRAINER"))
        .andExpect(flash().attribute("successMessage", "招待コードを発行しました。"));

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }

  @Test
  void ORG_ADMINは会員用をジム宛てに従来どおり発行できる() throws Exception {
    User orgAdmin = loginAs("ROLE_ORG_ADMIN");
    when(organizationScopeResolver.canAccessOrganization(orgAdmin, GYM_ID)).thenReturn(true);

    mvc.perform(
            post("/admin/invite-codes")
                .param("organizationId", GYM_ID.toString())
                .param("purpose", "MEMBER"))
        .andExpect(flash().attribute("successMessage", "招待コードを発行しました。"));

    verify(inviteCodeDao).insert(any(InviteCode.class));
  }
}
