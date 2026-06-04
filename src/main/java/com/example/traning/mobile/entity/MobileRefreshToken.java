package com.example.traning.mobile.entity;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.Data;

@Entity
@Table(name = "mobile_refresh_tokens")
@Data
public class MobileRefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt = LocalDateTime.now();
}
