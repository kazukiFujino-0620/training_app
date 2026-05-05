package com.example.traning.user;

import java.time.LocalDateTime;

import org.seasar.doma.Column;
import org.seasar.doma.Entity;
import org.seasar.doma.GeneratedValue;
import org.seasar.doma.GenerationType;
import org.seasar.doma.Id;
import org.seasar.doma.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Entity(immutable = true)
@Table(name = "users")
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
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
	public final Boolean enabled;

	@Column(name = "create_Datetime")
	public final LocalDateTime createDatetime;

	@Column(name = "update_Datetime")
	public final LocalDateTime updatedDatetime;
}
