package com.example.traning.mobile.service;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final long ACCESS_TOKEN_VALIDITY_MS   = 15L * 60 * 1000;            // 15分
	private static final long REFRESH_TOKEN_VALIDITY_MS  = 30L * 24 * 60 * 60 * 1000;  // 30日
	private static final long MFA_TEMP_TOKEN_VALIDITY_MS = 5L  * 60 * 1000;            // 5分

	@Value("${app.jwt.secret}")
	private String secretKeyBase64;

	private SecretKey signingKey() {
		byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
		return Keys.hmacShaKeyFor(keyBytes);
	}

	public String generateAccessToken(Long userId, String email, String role) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("email", email)
				.claim("role", role)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + ACCESS_TOKEN_VALIDITY_MS))
				.signWith(signingKey())
				.compact();
	}

	/** MFA検証用の短命トークン（5分）。デバイスIDを埋め込む。 */
	public String generateMfaTempToken(Long userId, String deviceId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("type", "mfa-pending")
				.claim("deviceId", deviceId)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + MFA_TEMP_TOKEN_VALIDITY_MS))
				.signWith(signingKey())
				.compact();
	}

	/** MFA仮トークンを検証し、Claims を返す。type が mfa-pending でない場合は例外。 */
	public Claims parseMfaTempToken(String token) throws JwtException {
		Claims claims = Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		if (!"mfa-pending".equals(claims.get("type", String.class))) {
			throw new JwtException("Invalid MFA token type");
		}
		return claims;
	}

	public Claims parseAccessToken(String token) throws JwtException {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public Long extractUserId(String token) {
		return Long.parseLong(parseAccessToken(token).getSubject());
	}

	public long getRefreshTokenValidityMs() {
		return REFRESH_TOKEN_VALIDITY_MS;
	}
}
