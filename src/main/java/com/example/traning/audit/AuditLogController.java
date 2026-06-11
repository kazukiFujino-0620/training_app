package com.example.traning.audit;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

  private final AuditLogService auditLogService;

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

    List<AuditLogEntry> logs = auditLogService.findForAdmin(userId, action, from, to, page);
    int total = auditLogService.countForAdmin(userId, action, from, to);
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
}
