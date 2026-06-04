package com.example.traning.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.traning.mobile.service.JwtService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

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
