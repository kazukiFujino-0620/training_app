package com.example.traning.mobile.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TokenResponse {

	private String accessToken;
	private String refreshToken;
	private long expiresIn;
	private boolean mfaRequired;
	private String mfaTempToken;

	/** MFAなし通常発行 */
	public static TokenResponse full(String accessToken, String refreshToken, long expiresIn) {
		TokenResponse r = new TokenResponse();
		r.setAccessToken(accessToken);
		r.setRefreshToken(refreshToken);
		r.setExpiresIn(expiresIn);
		r.setMfaRequired(false);
		return r;
	}

	/** MFA必要 — 仮トークンのみ返却 */
	public static TokenResponse mfaPending(String mfaTempToken) {
		TokenResponse r = new TokenResponse();
		r.setMfaRequired(true);
		r.setMfaTempToken(mfaTempToken);
		return r;
	}
}
