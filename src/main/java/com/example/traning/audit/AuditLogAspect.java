package com.example.traning.audit;

import com.example.traning.filter.IpUtils;
import com.example.traning.user.User;
import com.example.traning.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

  private final AuditLogService auditLogService;
  private final UserService userService;

  @Around("@annotation(auditLog)")
  public Object intercept(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
    Object result = pjp.proceed();

    try {
      Long userId = resolveUserId();
      String ip = resolveIp();
      String path = resolveRequestPath();
      Long targetId = extractTargetId(pjp, result);

      auditLogService.record(userId, auditLog.action(), auditLog.targetTable(), targetId, ip, path);
    } catch (Exception e) {
      log.error("監査ログの記録に失敗しました: action={} error={}", auditLog.action(), e.getMessage(), e);
      throw e;
    }

    return result;
  }

  private Long resolveUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
      return null;
    }
    // JWT認証の場合、principal は Long userId（JwtAuthenticationFilter で設定）
    if (auth.getPrincipal() instanceof Long userId) {
      return userId;
    }
    try {
      User user = userService.getUserByEmail(auth.getName());
      return user.getUserId().longValue();
    } catch (Exception e) {
      log.warn("監査ログのユーザーID取得失敗: name={}", auth.getName());
      return null;
    }
  }

  private String resolveIp() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return "unknown";
    return IpUtils.resolveClientIp(attrs.getRequest());
  }

  private String resolveRequestPath() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) return "";
    return attrs.getRequest().getRequestURI();
  }

  private Long extractTargetId(ProceedingJoinPoint pjp, Object result) {
    // パスパラメータの Long 型引数を優先使用
    for (Object arg : pjp.getArgs()) {
      if (arg instanceof Long l) return l;
    }
    // Long 直接返却（apiSaveTraining 等）
    if (result instanceof Long l) return l;
    // ResponseEntity<Long> からボディを取得（新規作成時のID返却）
    if (result instanceof ResponseEntity<?> re && re.getBody() instanceof Long l) {
      return l;
    }
    return null;
  }
}
