package com.example.traning.audit;

import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasAnyRole('ADMIN', 'ORG_ADMIN', 'STORE_ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

  private final AuditLogService auditLogService;
  private final UserService userService;
  private final OrganizationScopeResolver organizationScopeResolver;

  @GetMapping
  public String list(
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "1") int page,
      Model model) {

    if (from == null) from = LocalDate.now().minusDays(30);
    if (to == null) to = LocalDate.now();
    if (page < 1) page = 1;

    // ita1-1 フェーズ3: 組織スコープで監査ログを絞り込む（ROLE_ADMIN はフィルタなし）。
    Set<Long> accessibleOrganizationIds =
        organizationScopeResolver.resolveAccessibleOrganizationIds(getCurrentAdminUser());
    List<Long> organizationIds =
        accessibleOrganizationIds == null ? null : new ArrayList<>(accessibleOrganizationIds);

    List<AuditLogEntry> logs =
        auditLogService.findForAdmin(userId, action, from, to, page, organizationIds);
    int total = auditLogService.countForAdmin(userId, action, from, to, organizationIds);
    int totalPages = Math.max(1, (int) Math.ceil((double) total / 50));

    model.addAttribute("logs", logs);
    model.addAttribute("total", total);
    model.addAttribute("totalPages", totalPages);
    model.addAttribute("currentPage", page);
    model.addAttribute("filterUserId", userId);
    model.addAttribute("filterAction", action);
    model.addAttribute("filterFrom", from);
    model.addAttribute("filterTo", to);

    return "admin/audit_logs";
  }

  private User getCurrentAdminUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String email = (principal instanceof UserDetails ud) ? ud.getUsername() : "";
    return userService.getUserByEmail(email);
  }
}
