package com.example.traning.entity;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

@Entity(immutable = true)
@Table(name = "users")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	public final Integer userId;

	public final String email;
	public final String password;

	@Column(name = "user_Name")
	public final String userName;
	public final String role;
	public final boolean enabled;

	@Column(name = "create_Datetime")
	public final LocalDateTime createDatetime;

	@Column(name = "update_Datetime")
	public final LocalDateTime updatedDatetime;

	// 手動でコンストラクタを作成（引数の数はフィールドと同じ8個にする）
	public User(Integer userId, String email, String password, String userName, String role, boolean enabled,
			LocalDateTime createDatetime, LocalDateTime updatedDatetime) {
		this.userId = userId;
		this.email = email;
		this.password = password;
		this.userName = userName;
		this.role = role;
		this.enabled = enabled;
		this.createDatetime = createDatetime;
		this.updatedDatetime = updatedDatetime;
	}

	// Getter（ImmutableなのでGetterだけでOK）
	public Integer getUserId() {
		return userId;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public String getUserName() {
		return userName;
	}

	public String getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public LocalDateTime getCreateDatetime() {
		return createDatetime;
	}

	public LocalDateTime getUpdatedDatetime() {
		return updatedDatetime;
	}
}