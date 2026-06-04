package com.example.traning.mobile.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.traning.dao.UserDao;
import com.example.traning.mobile.dao.MobileRefreshTokenDao;
import com.example.traning.mobile.dto.LoginRequest;
import com.example.traning.mobile.dto.RefreshRequest;
import com.example.traning.mobile.dto.TokenResponse;
import com.example.traning.mobile.entity.MobileRefreshToken;
import com.example.traning.user.User;

@Service
public class MobileAuthService {

	private static final long ACCESS_TOKEN_EXPIRES_IN_SEC = 15 * 60L;

	private final JwtService jwtService;
	private final PasswordEncoder passwordEncoder;
	private final UserDao userDao;
	private final MobileRefreshTokenDao refreshTokenDao;

	public MobileAuthService(JwtService jwtService,
			PasswordEncoder passwordEncoder,
			UserDao userDao,
			MobileRefreshTokenDao refreshTokenDao) {
		this.jwtService = jwtService;
		this.passwordEncoder = passwordEncoder;
		this.userDao = userDao;
		this.refreshTokenDao = refreshTokenDao;
	}

	@Transactional
	public TokenResponse login(LoginRequest req) {
		User user = userDao.selectByEmail(req.getEmail())
				.orElseThrow(() -> new IllegalArgumentException("メールアドレスまたはパスワードが正しくありません"));

		if (user.getPassword() == null
				|| !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
			throw new IllegalArgumentException("メールアドレスまたはパスワードが正しくありません");
		}
		if (Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
			throw new IllegalArgumentException("アカウントが無効です");
		}

		Long userId = user.getUserId().longValue();
		String accessToken = jwtService.generateAccessToken(userId, user.getEmail(), user.getRole());

		String rawRefreshToken = UUID.randomUUID().toString();
		String tokenHash = sha256(rawRefreshToken);

		// 同デバイスの古いトークンを削除してから新規保存
		refreshTokenDao.deleteByUserIdAndDeviceId(userId, req.getDeviceId());

		MobileRefreshToken entity = new MobileRefreshToken();
		entity.setUserId(userId);
		entity.setTokenHash(tokenHash);
		entity.setDeviceId(req.getDeviceId());
		entity.setExpiresAt(LocalDateTime.now()
				.plusSeconds(jwtService.getRefreshTokenValidityMs() / 1000));
		refreshTokenDao.insert(entity);

		return new TokenResponse(accessToken, rawRefreshToken, ACCESS_TOKEN_EXPIRES_IN_SEC);
	}

	@Transactional
	public TokenResponse refresh(RefreshRequest req) {
		String tokenHash = sha256(req.getRefreshToken());
		MobileRefreshToken stored = refreshTokenDao.selectByTokenHash(tokenHash)
				.orElseThrow(() -> new IllegalArgumentException("リフレッシュトークンが無効です"));

		if (stored.getRevokedAt() != null
				|| stored.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("リフレッシュトークンが期限切れです");
		}

		User user = userDao.selectById(stored.getUserId().intValue());
		if (user == null || Boolean.FALSE.equals(user.getEnabled()) || user.getDeletedAt() != null) {
			throw new IllegalArgumentException("ユーザーが見つかりません");
		}

		// 旧トークンを無効化
		refreshTokenDao.revokeByTokenHash(stored.getTokenHash(), LocalDateTime.now());

		Long userId = user.getUserId().longValue();
		String accessToken = jwtService.generateAccessToken(userId, user.getEmail(), user.getRole());

		String rawRefreshToken = UUID.randomUUID().toString();
		String newHash = sha256(rawRefreshToken);

		MobileRefreshToken newEntity = new MobileRefreshToken();
		newEntity.setUserId(userId);
		newEntity.setTokenHash(newHash);
		newEntity.setDeviceId(stored.getDeviceId());
		newEntity.setExpiresAt(LocalDateTime.now()
				.plusSeconds(jwtService.getRefreshTokenValidityMs() / 1000));
		refreshTokenDao.insert(newEntity);

		return new TokenResponse(accessToken, rawRefreshToken, ACCESS_TOKEN_EXPIRES_IN_SEC);
	}

	@Transactional
	public void logout(Long userId, String deviceId) {
		refreshTokenDao.deleteByUserIdAndDeviceId(userId, deviceId);
	}

	/** SHA-256ハッシュ（リフレッシュトークンのインデックス用、BCryptと異なり決定論的） */
	private static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
