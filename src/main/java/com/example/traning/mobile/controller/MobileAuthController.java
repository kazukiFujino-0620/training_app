package com.example.traning.mobile.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.mobile.dto.LoginRequest;
import com.example.traning.mobile.dto.RefreshRequest;
import com.example.traning.mobile.dto.TokenResponse;
import com.example.traning.mobile.service.MobileAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

	private final MobileAuthService authService;

	public MobileAuthController(MobileAuthService authService) {
		this.authService = authService;
	}

	/** ログイン → JWT発行 */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
		TokenResponse tokens = authService.login(req);
		return ResponseEntity.ok(tokens);
	}

	/** アクセストークンをリフレッシュ */
	@PostMapping("/refresh")
	public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
		TokenResponse tokens = authService.refresh(req);
		return ResponseEntity.ok(tokens);
	}

	/** ログアウト（リフレッシュトークン削除） */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			@AuthenticationPrincipal Long userId,
			@RequestBody(required = false) RefreshRequest req) {
		String deviceId = (req != null) ? req.getDeviceId() : null;
		if (userId != null && deviceId != null) {
			authService.logout(userId, deviceId);
		}
		return ResponseEntity.noContent().build();
	}
}
