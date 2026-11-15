package com.example.traning.health;

import com.example.traning.audit.AuditLogService;
import com.example.traning.filter.IpUtils;
import com.example.traning.organization.GymSettingService;
import com.example.traning.organization.MemberAccessPolicy;
import com.example.traning.organization.OrganizationScopeResolver;
import com.example.traning.user.Role;
import com.example.traning.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会員の「健康に関わる情報」を誰が見られるかの判定（機能見直し-1-#15 Q15-2）。
 *
 * <p>対象: 今は #5 の睡眠・安静時心拍、#4 の除外理由。今後の #13 のやり取り（痛みの報告）、#14 の動画、#16 の問診、#17 のけがの記録も、
 * スタッフに見せる前に必ずここを通す。
 *
 * <ul>
 *   <li>本人: 見られる
 *   <li>担当トレーナー（役割を問わず、{@code users.assigned_trainer_id}が自分）: 見られる
 *   <li>店舗管理者・ジム管理者・システム管理者: 範囲内の会員で、会員が所属するジムの設定（{@code
 *       gym_settings.staff_health_info_visible}、初期値: 見られない）が「見られる」の場合のみ。閲覧は監査ログに記録する
 *   <li>担当外のトレーナー・その他: 見られない
 * </ul>
 */
@Component
public class HealthInfoAccessPolicy {

  /** 監査ログのアクション名。管理者がジムの設定にもとづいて健康に関わる情報を見たとき記録する。 */
  public static final String AUDIT_ACTION = "HEALTH_INFO_VIEW";

  /** 判定結果。{@code auditRequired} は、管理者がジムの設定にもとづいて見る場合に true。 */
  public record Decision(boolean allowed, boolean auditRequired) {
    static final Decision DENY = new Decision(false, false);
    static final Decision ALLOW = new Decision(true, false);
    static final Decision ALLOW_WITH_AUDIT = new Decision(true, true);
  }

  private final OrganizationScopeResolver organizationScopeResolver;
  private final GymSettingService gymSettingService;
  private final AuditLogService auditLogService;

  public HealthInfoAccessPolicy(
      OrganizationScopeResolver organizationScopeResolver,
      GymSettingService gymSettingService,
      AuditLogService auditLogService) {
    this.organizationScopeResolver = organizationScopeResolver;
    this.gymSettingService = gymSettingService;
    this.auditLogService = auditLogService;
  }

  public Decision decide(User viewer, User member) {
    if (viewer == null || member == null || viewer.getUserId() == null) {
      return Decision.DENY;
    }
    if (viewer.getUserId().equals(member.getUserId())) {
      return Decision.ALLOW;
    }
    Role role = Role.fromValue(viewer.getRole());
    if (role == null || role == Role.USER) {
      return Decision.DENY;
    }
    if (role.canBeAssignedTrainer() && MemberAccessPolicy.isAssignedTo(member, viewer)) {
      return Decision.ALLOW;
    }
    if (role == Role.ADMIN || role == Role.ORG_ADMIN || role == Role.STORE_ADMIN) {
      if (organizationScopeResolver.canAccessOrganization(viewer, member.getOrganizationId())
          && gymSettingService.isStaffHealthInfoVisible(member.getOrganizationId())) {
        return Decision.ALLOW_WITH_AUDIT;
      }
    }
    return Decision.DENY;
  }

  public boolean canView(User viewer, User member) {
    return decide(viewer, member).allowed();
  }

  /**
   * 健康に関わる情報を表示する直前に呼ぶ。見られない場合は403を投げ、管理者がジムの設定にもとづいて見る場合は監査ログに記録する。
   *
   * @param targetTable 監査ログに残す情報の種類（例: {@code "health_sleep"}）
   */
  public void requireViewAndRecord(User viewer, User member, String targetTable) {
    Decision decision = decide(viewer, member);
    if (!decision.allowed()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この会員の健康に関わる情報を見る権限がありません");
    }
    if (decision.auditRequired()) {
      auditLogService.record(
          viewer.getUserId().longValue(),
          AUDIT_ACTION,
          targetTable,
          member.getUserId().longValue(),
          currentIp(),
          currentPath());
    }
  }

  private static String currentIp() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return IpUtils.resolveClientIp(attrs.getRequest());
    }
    return "unknown";
  }

  private static String currentPath() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest().getRequestURI();
    }
    return "";
  }
}
