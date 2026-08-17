package com.example.traning.notice;

import com.example.traning.organization.Organization;
import com.example.traning.organization.OrganizationDao;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** ita2-5: トレーニングジム案内メッセージ対応。一般ユーザー向け閲覧・管理者向け配信の両方をまとめて扱う。 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class NoticeController {

  private final NoticeService noticeService;
  private final UserService userService;
  private final OrganizationDao organizationDao;
  private final OrganizationScopeResolver organizationScopeResolver;

  private User currentUser(Principal principal) {
    return userService.getUserByEmail(principal.getName());
  }

  // ── 一般ユーザー向け ─────────────────────────────────────────────

  @GetMapping("/notices")
  public String list(Model model, Principal principal) {
    User user = currentUser(principal);
    List<Notice> notices = noticeService.getActiveForUser(user);
    model.addAttribute("notices", notices);
    return "notice/list";
  }

  @PostMapping("/api/notices/{id}/dismiss")
  @ResponseBody
  public ResponseEntity<Void> dismiss(@PathVariable Long id, Principal principal) {
    User user = currentUser(principal);
    noticeService.dismiss(id, user.getUserId().longValue());
    return ResponseEntity.ok().build();
  }

  // ── 管理者向け（ROLE_ADMIN / ROLE_ORG_ADMIN / ROLE_STORE_ADMIN） ────────

  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN')")
  @GetMapping("/notices/manage")
  public String manage(
      @RequestParam(required = false) Long organizationId, Model model, Principal principal) {
    User admin = currentUser(principal);
    Set<Long> accessible = organizationScopeResolver.resolveAccessibleOrganizationIds(admin);

    List<Organization> organizations =
        accessible == null
            ? organizationDao.selectAll()
            : organizationDao.selectByIds(List.copyOf(accessible));
    model.addAttribute("organizations", organizations);

    Long targetOrgId =
        organizationId != null
                && organizations.stream().anyMatch(o -> o.getId().equals(organizationId))
            ? organizationId
            : organizations.stream()
                .map(Organization::getId)
                .filter(id -> id.equals(admin.getOrganizationId()))
                .findFirst()
                .orElse(organizations.isEmpty() ? null : organizations.get(0).getId());

    if (targetOrgId != null) {
      model.addAttribute("notices", noticeService.listForAdmin(admin, targetOrgId));
      model.addAttribute("selectedOrganizationId", targetOrgId);
    } else {
      model.addAttribute("notices", List.of());
    }
    return "notice/manage";
  }

  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN')")
  @PostMapping("/api/notices")
  @ResponseBody
  public ResponseEntity<?> create(@RequestBody NoticeCreateRequest request, Principal principal) {
    User admin = currentUser(principal);
    try {
      Notice notice =
          noticeService.create(
              admin, request.getOrganizationId(), request.getTitle(), request.getBody());
      return ResponseEntity.ok(notice);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN')")
  @PostMapping("/api/notices/{id}/delete")
  @ResponseBody
  public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
    User admin = currentUser(principal);
    try {
      noticeService.delete(admin, id);
      return ResponseEntity.ok().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
  }

  @Data
  public static class NoticeCreateRequest {
    private Long organizationId;
    private String title;
    private String body;
  }
}
