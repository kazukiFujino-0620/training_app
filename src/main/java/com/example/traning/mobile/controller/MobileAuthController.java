package com.example.traning.mobile.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.traning.audit.AuditLog;
import com.example.traning.mobile.dto.LoginRequest;
import com.example.traning.mobile.dto.MfaVerifyRequest;
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

	/** ログイン → JWT発行（MFA有効時は仮トークン返却） */
	@PostMapping("/login")
	public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
		TokenResponse tokens = authService.login(req);
		return ResponseEntity.ok(tokens);
	}

	/** MFA検証 → JWT発行（mfaRequired=true のあとに呼び出す） */
	@PostMapping("/mfa/verify")
	@AuditLog(action = "MOBILE_MFA_VERIFY", targetTable = "mobile_refresh_tokens")
	public ResponseEntity<TokenResponse> mfaVerify(@Valid @RequestBody MfaVerifyRequest req) {
		TokenResponse tokens = authService.verifyMfa(req);
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
	@AuditLog(action = "MOBILE_LOGOUT", targetTable = "mobile_refresh_tokens")
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
