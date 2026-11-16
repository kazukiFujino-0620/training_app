package com.example.traning.filter;

import com.example.traning.dao.UserDao;
import com.example.traning.mobile.service.JwtService;
import com.example.traning.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final UserDao userDao;

  public JwtAuthenticationFilter(JwtService jwtService, UserDao userDao) {
    this.jwtService = jwtService;
    this.userDao = userDao;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      sendUnauthorized(response, "Authorization header missing or invalid");
      return;
    }

    String token = authHeader.substring(7);
    try {
      Claims claims = jwtService.parseAccessToken(token);
      Long userId = Long.parseLong(claims.getSubject());
      String role = claims.get("role", String.class);

      // itバグ-48: 役割の変更前に発行されたトークン（DBの役割と一致しないもの）は使えなくする。
      // 401 を受けたアプリはリフレッシュを試みるが、役割の変更時にリフレッシュトークンも削除しているためログイン画面に戻る。
      User user = userDao.selectById(userId.intValue());
      if (user == null || !Objects.equals(user.getRole(), role)) {
        sendUnauthorized(response, "Invalid or expired token");
        return;
      }

      List<SimpleGrantedAuthority> authorities =
          List.of(new SimpleGrantedAuthority("ROLE_" + role));

      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(userId, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(auth);

    } catch (JwtException e) {
      sendUnauthorized(response, "Invalid or expired token");
      return;
    }

    chain.doFilter(request, response);
  }

  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }

  /** /api/mobile/auth/** は認証不要のためフィルターをスキップ */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith("/api/mobile/auth/");
  }
}
