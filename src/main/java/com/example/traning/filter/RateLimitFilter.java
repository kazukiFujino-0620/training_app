package com.example.traning.filter;

import com.example.traning.user.service.CustomUserDetails;
import com.example.traning.user.service.IpBlockService;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * APIレート制限フィルター。
 *
 * <p>制限ルール（デフォルト値。環境変数で上書き可能）: POST /login → 10回/分 (IPベース) POST /signup → 5回/時 (IPベース)
 * POST/PUT/DELETE /api/** → 60回/分 (userId or IP) GET /api/** → 300回/分 (userId or IP) 上記以外 → 制限なし
 *
 * <p>超過時: HTTP 429 + JSON レスポンスを返す。 既存の LoginAttemptService（メールベースのロック）と役割が異なるため並存する。
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimitBucketManager bucketManager;
  private final IpBlockService ipBlockService;

  public RateLimitFilter(RateLimitBucketManager bucketManager, IpBlockService ipBlockService) {
    this.bucketManager = bucketManager;
    this.ipBlockService = ipBlockService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String path = req.getRequestURI();
    String method = req.getMethod();
    String ip = IpUtils.resolveClientIp(req);

    log.debug("RateLimitFilter invoked: method={}, path={}, ip={}", method, path, ip);

    // IPブロックチェック（POST /login のみ）
    if ("POST".equals(method) && "/login".equals(path)) {
      if (ipBlockService.isBlocked(ip)) {
        log.warn("Blocked IP attempted login: ip={}", maskIp(ip));
        res.setStatus(429);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter()
            .write("{\"error\":\"アクセスが一時的に" + "ブロックされています。" + "しばらくしてから再試行" + "してください。\"}");
        return;
      }
    }

    Bucket bucket = resolveBucket(path, method, ip, req);

    if (bucket == null) {
      chain.doFilter(req, res);
      return;
    }

    if (bucket.tryConsume(1)) {
      chain.doFilter(req, res);
    } else {
      log.warn("Rate limit exceeded: path={}, method={}, ip={}", path, method, ip);
      res.setStatus(429);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter()
          .write(
              "{\"error\":\"\\u30ea\\u30af\\u30a8\\u30b9\\u30c8\\u304c\\u591a\\u3059\\u304e\\u307e\\u3059\\u3002"
                  + "\\u3057\\u3070\\u3089\\u304f\\u3057\\u3066\\u304b\\u3089\\u518d\\u8a66\\u884c\\u3057\\u3066\\u304f\\u3060\\u3055\\u3044\\u3002\"}");
    }
  }

  private Bucket resolveBucket(String path, String method, String ip, HttpServletRequest req) {
    if (isSkipPath(path)) return null;

    if ("POST".equals(method) && "/login".equals(path)) {
      return bucketManager.loginBucket(ip);
    }

    if ("POST".equals(method) && "/signup".equals(path)) {
      return bucketManager.signupBucket(ip);
    }

    if (path.startsWith("/api/") || path.startsWith("/admin/api/")) {
      String key = resolveUserKey(req, ip);
      return "GET".equals(method)
          ? bucketManager.apiReadBucket(key)
          : bucketManager.apiWriteBucket(key);
    }

    return null;
  }

  private boolean isSkipPath(String path) {
    return path.startsWith("/css/")
        || path.startsWith("/js/")
        || path.startsWith("/images/")
        || path.startsWith("/swagger-ui/")
        || path.startsWith("/v3/api-docs/");
  }

  /** 認証済みユーザーは userId、未認証は IP をキーにする。 */
  private String resolveUserKey(HttpServletRequest req, String ip) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
      Object principal = auth.getPrincipal();
      if (principal instanceof CustomUserDetails ud) {
        return "userId:" + ud.getUserId();
      }
      if (principal instanceof UserDetails ud) {
        // フォームログインユーザー: メールをハッシュ化してプライバシー保護
        return "user:" + Integer.toHexString(ud.getUsername().hashCode());
      }
    }
    return "ip:" + ip;
  }

  private String maskIp(String ip) {
    int lastDot = ip.lastIndexOf('.');
    return lastDot > 0 ? ip.substring(0, lastDot) + ".***" : ip;
  }
}
